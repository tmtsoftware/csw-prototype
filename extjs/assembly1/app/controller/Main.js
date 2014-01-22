/*
 * Main controller: implements the actions for the form.
 *
 * Note: This code is a bit more complicated that it would normally be, since we are not going with
 * the normal ExtJS way of doing things. The "get" from the server has to POST the JSON for the
 * requested form values (not normally done in ExtJS), and the JSON has multiple levels that also
 * complicate things.
 *
 * For this demo, we are also displaying checkboxes for saved fields, only saving modified fields,
 * displaying feedback for each field as it is updated, etc.
 *
 * It should be possible to extract most of this code into a reusable class.
 *
 */

Ext.define('Assembly1.controller.Main', {
    extend: 'Ext.app.Controller',

    // Initialize the callbacks
    init: function (application) {
        this.control({
            "button#applyButton": {
                click: this.submitForm
            },
            "button#refreshButton": {
                click: this.refreshForm
            }
        });
    },

    // Gets the current values to display
    refreshForm: function (button) {
        this.application.getMobieBluesStore().load();
    },

    // Called for the Submit button: Get the form values and post them to the HTTP server in
    // the correct JSON format.
    // TODO: Factor out reusable parts
    submitForm: function (button) {
        var me = this;
        var formPanel = button.up('form');
        var filterCb = formPanel.down('#filterCb');
        var disperserCb = formPanel.down('#disperserCb');
        if (!filterCb.isDirty() && !disperserCb.isDirty()) return;
        var progressBar = button.up('app-main').down('progressbar');

        // Check marks to show when one of the items has completed
        var filterDone = formPanel.down('#filterDone');
        filterDone.hide();
        var disperserDone = formPanel.down('#disperserDone');
        disperserDone.hide();

        // Display a busy cursor (mask) over each item
        var disperserMask = new Ext.LoadMask(disperserCb, {msg:""});
        var filterMask = new Ext.LoadMask(filterCb, {msg:""});

        var v = formPanel.getForm().getValues(false, true, false);
        var m = Ext.create("Assembly1.model.MobieBlue", { filter: v.filter, disperser: v.disperser });

        m.save({
            success: function(record, operation)
            {
                var result = Ext.decode(operation.response.responseText);
                var runId = result["runId"];
                console.log("Submitted command with runId: " + runId);
                Ext.getCmp("applyButton").disabled = true;
                progressBar.wait({
                    text: 'Updating...'
                });
                if (filterCb.isDirty()) filterMask.show();
                if (disperserCb.isDirty()) disperserMask.show();

                pollCommandStatus(runId)
            },
            failure: function(record, operation)
            {
                var statusCode = operation.response.status;
                var statusText = operation.response.statusText;
                progressBar.reset();
                progressBar.updateText("Error: " + statusCode + ' (' + statusText + ')');
                Ext.getCmp("applyButton").disabled = false;
            }
        });

        // Does long polling to the Spray/REST command server while waiting for the command to complete.
        // The command status is displayed in a progress bar.
        var pollCommandStatus = function (runId) {
            Ext.Ajax.request({
                url: '/config/' + runId + '/status',
                method: 'GET',
                success: function (response, options) {
                    var result = JSON.parse(response.responseText);
                    var status = result["name"];
                    progressBar.updateText("Status: " + status);
                    console.log("Command status: " + status);

                    // Display check mark for completed items (for the demo)
                    if (result["partiallyDone"]) {
                        var path = result["message"];
                        var partialStatus = result["status"];
                        if (path == "config.tmt.mobie.blue.filter") {
                            if (filterCb.isDirty()) {
                                filterDone.show();
                                filterMask.hide();
                            }
                        } else if (path == "config.tmt.mobie.blue.disperser") {
                            if (disperserCb.isDirty()) {
                                disperserDone.show();
                                disperserMask.hide();
                            }
                        }
                    }

                    if (result["done"]) {
                        if (filterCb.isDirty()) {
                            filterDone.show();
                            filterMask.hide();
                        }
                        if (disperserCb.isDirty()) {
                            disperserDone.show();
                            disperserMask.hide();
                        }

                        progressBar.reset();
                        Ext.getCmp("applyButton").disabled = false;
                        if (status == "Error") {
                            progressBar.updateText("Error: " + result["message"]);
                        }

                        // After submit, query the values to make sure we have the actual values.
                        // (This also solves a problem handling the "dirty" state of the fields, since that is cleared
                        //  when the values are set. We could also do this directly to save time...)
                        me.refreshForm(button);
                    } else {
                        pollCommandStatus(runId);
                    }
                },
                failure: function (response, options) {
                    var statusCode = response.status;
                    var statusText = response.statusText;
                    progressBar.reset();
                    progressBar.updateText("Error: " + statusCode + ' (' + statusText + ')');
                    Ext.getCmp("applyButton").disabled = false;
                }
            });
        };
    }
});
