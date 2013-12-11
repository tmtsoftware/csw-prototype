Ext.require([
    'Ext.form.*',
    'Ext.layout.container.Column',
    'Ext.tab.Panel'
]);

// The model for the top form: base position
Ext.define("App.model.BasePos", {
    extend: "Ext.data.Model",
    fields: ["posName", "c1", "c2", "equinox"]
});

// The model for the second form (AO pos)
Ext.define("App.model.AoPos", {
    extend: "Ext.data.Model",
    fields: ["c1", "c2", "equinox"]
});

// Sends an ajax query to the Spray/REST command server to get the current values to display
var refreshForm = function(basePosFormPanel, aoPosFormPanel){
    Ext.Ajax.request({
        url: 'get',
        method: 'POST',
        jsonData: {
            config: {
                tmt: {
                    tel: {
                        base: {
                            pos: {posName: "", c1: "", c2: "", equinox: ""}
                        }, ao: {
                            pos: {
                                one: {c1: "", c2: "", equinox: ""}
                            }
                        }
                    }
                }
            }
        },
        success: function(response, options){
            var result = Ext.decode(response.responseText);
            basePosFormPanel.getForm().setValues(result.config.tmt.tel.base.pos);
            aoPosFormPanel.getForm().setValues(result.config.tmt.tel.ao.pos.one);
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
var submitForm = function(basePosFormPanel, aoPosFormPanel){
    var b = basePosFormPanel.getForm().getValues();
    var a = aoPosFormPanel.getForm().getValues();
    var data = Ext.apply(
        {
            config: {
                tmt: {
                    tel: {
                        base: {
                            pos: {
                                posName: b.posName, c1: b.c1, c2: b.c2, equinox: b.equinox
                            }
                        },
                        ao: {
                            pos: {
                                one: {
                                    c1: a.c1, c2: a.c2, equinox: a.equinox
                                }
                            }
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

// Validator for RA values
Ext.apply(Ext.form.field.VTypes, {
    hhmmss: function (val, field) {
        var regex = /^\d\d:\d\d:\d\d(?:\.\d+)?$/;
        return regex.test(val);
    },
    hhmmssText: 'Expected coordinate as hh:mm:ss.sss.',
    hhmmssMask: /[\d:\.]/
});

// Validator for Dec values
Ext.apply(Ext.form.field.VTypes, {
    ddmmss: function (val, field) {
        var regex = /^\d\d:\d\d:\d\d(?:\.\d+)?$/;
        return regex.test(val);
    },
    ddmmssText: 'Expected coordinate as dd:mm:ss.sss.',
    ddmmssMask: /[\d:\.]/
});

// Validator for equinox values
Ext.apply(Ext.form.field.VTypes, {
    equinox: function (val, field) {
        var regex = /^[A-Z]\d\d\d\d$/;
        return regex.test(val);
    },
    equinoxText: 'Expected equinox as J2000, B1950, etc...',
    equinoxMask: /[A-Z0-9]/
});


Ext.onReady(function(){

    Ext.QuickTips.init();

    var basePosFormPanel = Ext.create('Ext.form.Panel', {
        model: App.model.BasePos,
        name: 'BasePosForm',
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
            title: 'Base Position',
            defaultType: 'textfield',
            layout: 'anchor',
            items: [{
                fieldLabel: 'Position Name',
                name: 'posName',
                allowBlank:false
            },{
                fieldLabel: 'C1',
                name: 'c1',
                vtype: 'hhmmss',
                allowBlank:false,
                emptyText: 'Enter hh:mm:ss.sss'
            },{
                fieldLabel: 'C2',
                name: 'c2',
                vtype: 'ddmmss',
                allowBlank:false,
                emptyText: 'Enter dd:mm:ss.sss'
            }, {
                fieldLabel: 'Equinox',
                name: 'equinox',
                vtype: 'equinox',
                allowBlank:false,
                emptyText: 'Example: J2000'
            }]
        }]
    });

    var aoPosFormPanel = Ext.create('Ext.form.Panel', {
        model: App.model.AoPos,
        name: 'AoPosForm',
        frame:true,
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
            title: 'AO Position',
            defaultType: 'textfield',
            layout: 'anchor',
            defaults: {
                anchor: '100%'
            },
            items: [{
                fieldLabel: 'C1',
                name: 'c1',
                vtype: 'hhmmss',
                allowBlank:false,
                emptyText: 'Enter hh:mm:ss.sss'
            },{
                fieldLabel: 'C2',
                name: 'c2',
                vtype: 'ddmmss',
                allowBlank:false,
                emptyText: 'Enter dd:mm:ss.sss'
            }, {
                fieldLabel: 'Equinox',
                name: 'equinox',
                vtype: 'equinox',
                allowBlank:false,
                emptyText: 'Example: J2000'
            }]
        }],
        buttons: [{
            text: 'Refresh',
            id: 'refreshButton',
            handler : function(){
                refreshForm(basePosFormPanel, aoPosFormPanel)
            }
        }, {
            text: 'Apply',
            id: 'applyButton',
            handler : function(){
                if (basePosFormPanel.isValid() && aoPosFormPanel.isValid()) {
                    submitForm(basePosFormPanel, aoPosFormPanel)
                }
            }
        }]
    });

    basePosFormPanel.render(document.body);
    aoPosFormPanel.render(document.body);
    progressBar.render(document.body);

    refreshForm(basePosFormPanel, aoPosFormPanel)
});

