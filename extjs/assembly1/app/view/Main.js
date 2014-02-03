Ext.define('Assembly1.view.Main', {
    extend: 'Ext.container.Container',
    requires:[
        'Ext.tab.Panel',
        'Ext.layout.container.Border',
        'Assembly1.view.Form'
    ],

    xtype: 'app-main',

    layout: {
        type: 'vbox',
        align: 'center',
        pack: 'center'
    },

    items: [
        {
            xtype: 'assembly1Form'
        },
        {
            xtype: 'progressbar',
            width:350
        }
    ]

});
