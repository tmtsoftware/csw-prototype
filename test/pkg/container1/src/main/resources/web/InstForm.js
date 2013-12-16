// Demo showing tmt.mobie.blue.filter and tmt.mobie.blue.disperser components

Ext.require([
    'Ext.form.*',
    'Ext.layout.container.Column',
    'Ext.tab.Panel'
]);

// The model for the form
Ext.define("Tmt.mobie.blue.Model", {
    extend: "Ext.data.Model",

    fields: ["filter", "pupilWheel", "disperser", "exposureTime"]
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
                            disperser: {value: ""}
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
                disperser: result.config.tmt.mobie.blue.disperser.value
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

// Filter list (TODO: make remote)
var filterStore = ({
    type: 'array',
    fields: ['name'],
    data: [
        ["None"],
        ["g_G0301"],
        ["r_G0303"],
        ["i_G0302"],
        ["z_G0304"],
        ["Z_G0322"],
        ["Y_G0323"],
        ["GG455_G0305"],
        ["OG515_G0306"],
        ["RG610_G0307"],
        ["CaT_G0309"],
        ["Ha_G0310"],
        ["HaC_G0311"],
        ["DS920_G0312"],
        ["SII_G0317"],
        ["OIII_G0318"],
        ["OIIIC_G0319"],
        ["HeII_G0320"],
        ["HeIIC_G0321"],
        ["HartmannA_G0313 + r_G0303"],
        ["HartmannB_G0314 + r_G0303"],
        ["g_G0301 + GG455_G0305"],
        ["g_G0301 + OG515_G0306"],
        ["r_G0303 + RG610_G0307"],
        ["i_G0302 + CaT_G0309"],
        ["z_G0304 + CaT_G0309"],
        ["u_G0308"]
    ]
});

// Disperser list (TODO: make remote)
var disperserStore = ({
    type: 'array',
    fields: ['name'],
    data: [
        ["Mirror"],
        ["B1200_G5301"],
        ["R831_G5302"],
        ["B600_G5303"],
        ["B600_G5307"],
        ["R600_G5304"],
        ["R400_G5305"],
        ["R150_G5306"]
    ]
});


Ext.onReady(function(){

    Ext.QuickTips.init();

    var formPanel = Ext.create('Ext.form.Panel', {
        model: Tmt.mobie.blue.Model,
        name: 'Form',
        frame: true,
        title: 'Assembly1 Configuration',
        bodyPadding: 10,
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
                xtype: 'combobox',
                name: 'filter',
                editable : false,
                fieldLabel: 'Filter',
                displayField: 'name',
                valueField: 'name',
                store: filterStore,
                queryMode: 'local', //or remote
                forceSelection: true
            },{
                xtype: 'combobox',
                name: 'disperser',
                editable : false,
                fieldLabel: 'Disperser',
                displayField: 'name',
                valueField: 'name',
                store: disperserStore,
                queryMode: 'local', //or remote
                forceSelection: true
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

