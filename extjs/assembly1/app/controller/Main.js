/*
  Main controller: implements the actions for the form
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

    submitForm: function (button) {
        var formPanel = button.up('form');
        var progressBar = button.up('app-main').down('progressbar');

        // Check marks to show when one of the items has completed
        var filterDone = formPanel.down('#filterDone');
        filterDone.hide();
        var disperserDone = formPanel.down('#disperserDone');
        disperserDone.hide();

        // Display a busy cursor over each item
        var diperserCb = formPanel.down('#disperser');
        var disperserMask = new Ext.LoadMask(diperserCb, {msg:""});
        var filterCb = formPanel.down('#filter');
        var filterMask = new Ext.LoadMask(filterCb, {msg:""});

        var v = formPanel.getForm().getValues();
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
                filterMask.show();
                disperserMask.show();
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
            console.log("Poll Command status");
            Ext.Ajax.request({
                url: '/config/' + runId + '/status',
                method: 'GET',
                success: function (response, options) {
                    var result = JSON.parse(response.responseText);
                    console.log("Command status JSON response: " + response.responseText);
                    var status = result["name"];
                    progressBar.updateText("Status: " + status);
                    console.log("Command status: " + status);

                    // Display check mark for completed items (for the demo)
                    if (result["partiallyDone"]) {
                        var path = result["message"];
                        var partialStatus = result["status"];
                        console.log("Partial Status for: " + path + " = " + partialStatus);
                        if (path == "config.tmt.mobie.blue.filter") {
                            filterDone.show();
                            filterMask.hide();
                        } else if (path == "config.tmt.mobie.blue.disperser") {
                            disperserDone.show();
                            disperserMask.hide();
                        }
                    }

                    if (result["done"]) {
                        filterDone.show();
                        filterMask.hide();
                        disperserDone.show();
                        disperserMask.hide();

                        progressBar.reset();
                        Ext.getCmp("applyButton").disabled = false;
                        if (status == "Error") {
                            progressBar.updateText("Error: " + result["message"]);
                        }
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
