Ext.define('Assembly1.Application', {
    extend: 'Ext.app.Application',
    name: 'Assembly1',

    views: [
        'Main',
        'Form'
    ],

    controllers: [
        'Main'
    ],

    stores: [
        'Assembly1.store.Filters',
        'Assembly1.store.Dispersers',
        'MobieBlues'
    ],

    launch: function() {
        var bodyElement = Ext.getBody();
        bodyElement.setStyle({'background-color':'#dae7f6'});
    }
});

