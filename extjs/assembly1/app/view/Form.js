

Ext.define("Assembly1.view.Form", {
    extend: 'Ext.form.Panel',
    requires:[
        'Ext.form.*',
        'Ext.layout.container.Column',
        'Assembly1.model.MobieBlue',
        'Assembly1.store.MobieBlues',
        'Assembly1.store.Filters',
        'Assembly1.store.Dispersers'
    ],
    model: 'Assembly1.model.MobieBlue',
    name: 'Form',
    id:'assembly1Form',
    xtype:'assembly1Form',
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
        layout: 'anchor',
        items: [{
            xtype:'fieldcontainer',
            layout:'hbox',
            items: [{
                xtype: 'combobox',
                name: 'filter',
                id: 'filter',
                editable: false,
                fieldLabel: 'Filter',
                displayField: 'name',
                valueField: 'name',
                store: 'Assembly1.store.Filters',
                queryMode: 'local', //or remote
                forceSelection: true
            }, {
                xtype: 'label',
                id: 'filterDone',
                html: '&#x2713;', // Check mark to show when the item has completed
                hidden: true,
                margin: '0 0 0 10'
            }]
        },{
            xtype:'fieldcontainer',
            layout:'hbox',
            items: [{
                xtype: 'combobox',
                name: 'disperser',
                id: 'disperser',
                editable : false,
                fieldLabel: 'Disperser',
                displayField: 'name',
                valueField: 'name',
                store: 'Assembly1.store.Dispersers',
                queryMode: 'local', //or remote
                forceSelection: true
            }, {

                xtype: 'label',
                id: 'disperserDone',
                html: '&#x2713;', // Check mark to show when the item has completed
                hidden: true,
                margin: '0 0 0 10'
            }]
        }]
    }],
    buttons: [{
            text: 'Refresh',
            id: 'refreshButton'
        }, {
            text: 'Apply',
            id: 'applyButton'
        }]
});
