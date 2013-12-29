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
                click: this.refreshForm,
                afterrender: this.refreshForm
            }
        });
    },

    // Gets the current values to display
    refreshForm: function (button) {
        var formPanel = button.up('form');
        console.log("Refresh form!");

        var data = Ext.apply(
            {
                config: {
                    tmt: {
                        mobie: {
                            blue: {
                                filter: {value: ""},
                                disperser: {value: ""}
                            }
                        }
                    }
                }
            }
        );
        var json = Ext.encode(data);
        Ext.Ajax.request({
            url: 'http://localhost:8089/get',
            method: 'POST',
            jsonData: json,
            success: function(response, options){
                var result = Ext.decode(response.responseText);
                console.log("XXX refreshForm: result = " + response.responseText)
                formPanel.getForm().setValues({
                    filter: result.config.tmt.mobie.blue.filter.value,
                    disperser: result.config.tmt.mobie.blue.disperser.value
                });
            },
            failure: function(response, options){
                var statusCode = response.status;
                var statusText = response.statusText;
                alert("Error: " + statusCode + ' (' + statusText + ')');
            }
        });
    },

    submitForm: function (button) {
        var formPanel = button.up('form');
        var progressBar = button.up('app-main').down('progressbar');
        console.log("Submit form! " + button);

        var v = formPanel.getForm().getValues();
        var data = Ext.apply(
            {
                config: {
                    tmt: {
                        mobie: {
                            blue: {
                                filter: {value: v.filter},
                                disperser: {value: v.disperser}
                            }
                        }
                    }
                }
            }
        );
        var json = Ext.encode(data);

        Ext.getCmp("applyButton").disabled = true;
        progressBar.wait({
            text: 'Updating...'
        });

        // Does long polling to the Spray/REST command server while waiting for the command to complete.
        // The command status is displayed in a progress bar.
        var pollCommandStatus = function (runId) {
            console.log("Poll Command status");
            Ext.Ajax.request({
                url: 'http://localhost:8089/config/' + runId + '/status',
                method: 'GET',
                success: function (response, options) {
                    var result = JSON.parse(response.responseText);
                    console.log("Command status JSON response: " + response.responseText);
                    var status = result["name"];
                    progressBar.updateText("Status: " + status);
                    console.log("Command status: " + status);
                    if (status != "Completed" && status != "Error" && status != "Aborted" && status != "Canceled") {
                        pollCommandStatus(runId);
                    } else {
                        progressBar.reset();
                        Ext.getCmp("applyButton").disabled = false;
                        if (status == "Error") {
                            progressBar.updateText("Error: " + result["message"]);
                        }
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
        }

        Ext.Ajax.request({
            url: 'http://localhost:8089/queue/submit',
            method: 'POST',
            jsonData: json,
            success: function(response, options){
                var result = Ext.decode(response.responseText);
                var runId = result["runId"];
                console.log("Submitted command with runId: " + runId);
                pollCommandStatus(runId)
            },
            failure: function(response, options){
                var statusCode = response.status;
                var statusText = response.statusText;
                progressBar.reset();
                progressBar.updateText("Error: " + statusCode + ' (' + statusText + ')');
                Ext.getCmp("applyButton").disabled = false;
            }
        });

    }

});
