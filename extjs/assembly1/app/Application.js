Ext.define('Assembly1.Application', {
    extend: 'Ext.app.Application',
    name: 'Assembly1',

    views: [
        // TODO: add views here
        'Main',
        'Form'
    ],

    controllers: [
        // TODO: add controllers here
        'Main'
    ],

    stores: [
        // TODO: add stores here
        'Assembly1.store.Filters',
        'Assembly1.store.Dispersers'
    ],

    launch: function() {
        var bodyElement = Ext.getBody();
        bodyElement.setStyle({'background-color':'#dae7f6'});
    }
});

