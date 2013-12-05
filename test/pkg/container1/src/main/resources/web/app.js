Ext.require([
    'Ext.form.*',
    'Ext.layout.container.Column',
    'Ext.tab.Panel'
]);


/*!
 * Demo: Displays a form for configuring the base position and one AO pos
 */
Ext.onReady(function(){

    Ext.QuickTips.init();

    var bd = Ext.getBody();

    var form = Ext.create('Ext.form.Panel', {
        url:'queue/submit',
        frame:true,
        title: 'Assembly1 Configuration',
        bodyStyle:'padding:5px 5px 0',
        width: 350,
        fieldDefaults: {
            msgTarget: 'side',
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
            defaults: {
                anchor: '100%'
            },
            items: [{
                fieldLabel: 'Position Name',
                name: 'posName',
                allowBlank:false
            },{
                fieldLabel: 'C1',
                name: 'c1'
            },{
                fieldLabel: 'C2',
                name: 'c2'
            }, {
                fieldLabel: 'Equinox',
                name: 'equinox'
            }]
        },{
            xtype:'fieldset',
            title: 'AO Position',
            defaultType: 'textfield',
            layout: 'anchor',
            defaults: {
                anchor: '100%'
            },
            items: [{
                fieldLabel: 'C1',
                name: 'c1'
            },{
                fieldLabel: 'C2',
                name: 'c2'
            }, {
                fieldLabel: 'Equinox',
                name: 'equinox'
            }]
        }],
        buttons: [{
            text: 'Apply',
            id: 'applyButton',
            handler : function(){
                var form = this.up('form')
                var v = form.getValues();
                console.log('XXX values ', v);
                var jdata = Ext.apply(
                    {config:
                        {tmt:
                            {tel:
                                {base:
                                    {pos:
                                        {posName: v.posName, c1: v.c1[0], c2: v.c2[0], equinox: v.equinox[0]}
                                    },
                                 ao:
                                    {pos:
                                        {one:
                                         {c1: v.c1[1], c2: v.c2[1], equinox: v.equinox[1]}
                                        }
                                    }
                                }
                            }
                        }
                    }
                );
                console.log('XXX jdata ', Ext.encode(jdata));
            }
        }]
    });

    form.render(document.body);

});

