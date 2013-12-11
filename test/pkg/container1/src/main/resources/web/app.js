Ext.require([
    'Ext.form.*',
    'Ext.layout.container.Column',
    'Ext.tab.Panel'
]);


Ext.define("App.model.BasePos", {
    extend: "Ext.data.Model",
    fields: ["posName", "c1", "c2", "equinox"]
});

Ext.define("App.model.AoPos", {
    extend: "Ext.data.Model",
    fields: ["c1", "c2", "equinox"]
});

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
            console.log('The Success function was called.');
            console.log(response.responseText);
            var result = Ext.decode(response.responseText);
            console.log("XXX pos = " + result.config.tmt.tel.base.pos);
            console.log("XXX posName = " + result.config.tmt.tel.base.pos.posName);
            console.log("XXX c1 = " + result.config.tmt.tel.base.pos.c1);

            basePosFormPanel.getForm().setValues(result.config.tmt.tel.base.pos);
            aoPosFormPanel.getForm().setValues(result.config.tmt.tel.ao.pos.one);
        },
        failure: function(response, options){
            console.log('The Failure function was called.');
            var statusCode = response.status;
            var statusText = response.statusText;
            console.log(statusCode + ' (' + statusText + ')');
        }
    });
};


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
    var json = Ext.encode(data)
    console.log("XXX " + json);

    Ext.Ajax.request({
        url: 'queue/submit',
        method: 'POST',
        jsonData: json,
        success: function(response, options){
            console.log('The Success function was called.');
            console.log(response.responseText);
            var result = Ext.decode(response.responseText);
        },
        failure: function(response, options){
            console.log('The Failure function was called.');
            var statusCode = response.status;
            var statusText = response.statusText;
            console.log(statusCode + ' (' + statusText + ')');
        }
    });
};

// Validators
Ext.apply(Ext.form.field.VTypes, {
    hhmmss: function (val, field) {
        var regex = /^\d\d:\d\d:\d\d(?:\.\d+)?$/;
        return regex.test(val);
    },
    hhmmssText: 'Expected coordinate as hh:mm:ss.sss.',
    hhmmssMask: /[\d:\.]/
});

Ext.apply(Ext.form.field.VTypes, {
    ddmmss: function (val, field) {
        var regex = /^\d\d:\d\d:\d\d(?:\.\d+)?$/;
        return regex.test(val);
    },
    ddmmssText: 'Expected coordinate as dd:mm:ss.sss.',
    ddmmssMask: /[\d:\.]/
});

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
                submitForm(basePosFormPanel, aoPosFormPanel)
            }
        }]
    });

    basePosFormPanel.render(document.body);
    aoPosFormPanel.render(document.body);
    refreshForm(basePosFormPanel, aoPosFormPanel)
});

