
Ext.define('Assembly1.store.MobieBlues', {
    extend:  'Ext.data.Store',
    model: 'Assembly1.model.MobieBlue',
    autoLoad: true,
    listeners: {
        // TODO: put this in a controller?
        load: function (store, records, success, operations) {
            var form = Ext.getCmp('assembly1Form');
            form.loadRecord(store.data.first());
        }
    }
});



