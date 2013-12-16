// Demo showing tmt.mobie.blue.filter and tmt.mobie.blue.grating components

Ext.require([
    'Ext.form.*',
    'Ext.layout.container.Column',
    'Ext.tab.Panel'
]);

// The model for the form
Ext.define("Tmt.mobie.blue.Model", {
    extend: "Ext.data.Model",

    fields: ["filter", "pupilWheel", "grating", "exposureTime"]
});

// Sends an ajax query to the Spray/REST command server to get the current values to display
var refreshForm = function(formPanel){
    Ext.Ajax.request({
        url: 'get',
        method: 'POST',
        jsonData: {
            config: {
                tmt: {
                    mobie: {
                        blue: {
                            filter: {value: ""},
                            grating: {value: ""}
                        }
                    }
                }
            }
        },
        success: function(response, options){
            var result = Ext.decode(response.responseText);
            console.log("XXX refreshForm: result = " + response.responseText)
            formPanel.getForm().setValues({
                filter: result.config.tmt.mobie.blue.filter.value,
                grating: result.config.tmt.mobie.blue.grating.value
            });
        },
        failure: function(response, options){
            var statusCode = response.status;
            var statusText = response.statusText;
            alert("Error: " + statusCode + ' (' + statusText + ')');
        }
    });
};

// Progress bar displayed while command is running
var progressBar = Ext.create('Ext.ProgressBar', {
    id:'progressBar',
    width:350
});


// Does long polling to the Spray/REST command server while waiting for the command to complete.
// The command status is displayed in a progress bar.
var pollCommandStatus = function (runId) {
    console.log("Poll Command status");
    Ext.Ajax.request({
        url: 'config/' + runId + '/status',
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
};


// Submits the form as JSON to the Spray/REST command service and then waits for the
// command to complete, displaying the status.
var submitForm = function(formPanel){
    var v = formPanel.getForm().getValues();
    var data = Ext.apply(
        {
            config: {
                tmt: {
                    mobie: {
                        blue: {
                            filter: {value: v.filter},
                            grating: {value: v.grating}
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

    Ext.Ajax.request({
        url: 'queue/submit',
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
            progressBar.reset()
            progressBar.updateText("Error: " + statusCode + ' (' + statusText + ')');
            Ext.getCmp("applyButton").disabled = false;
        }
    });
};

Ext.onReady(function(){

    Ext.QuickTips.init();

    var formPanel = Ext.create('Ext.form.Panel', {
        model: Tmt.mobie.blue.Model,
        name: 'Form',
        frame:true,
        title: 'Assembly1 Configuration',
        bodyStyle:'padding:5px 5px 0',
        width: 350,
        fieldDefaults: {
            msgTarget: 'under',
            labelWidth: 75
        },
        defaults: {
            anchor: '100%'
        },
        items: [{
            xtype:'fieldset',
            title: 'TMT Mobie Blue Configuration',
            defaultType: 'textfield',
            layout: 'anchor',
            items: [{
                fieldLabel: 'Filter',
                name: 'filter',
                allowBlank:false
            },{
                fieldLabel: 'Grating',
                name: 'grating',
                allowBlank:false
            }]
        }],
        buttons: [{
            text: 'Refresh',
            id: 'refreshButton',
            handler : function(){
                refreshForm(formPanel)
            }
        }, {
            text: 'Apply',
            id: 'applyButton',
            handler : function(){
                if (formPanel.isValid()) {
                    submitForm(formPanel)
                }
            }
        }]
    });

    formPanel.render(document.body);
    progressBar.render(document.body);

    refreshForm(formPanel)
});

