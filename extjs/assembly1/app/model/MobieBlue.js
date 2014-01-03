
Ext.define('Assembly1.model.MobieBlue', {
    extend: 'Ext.data.Model',

    fields: [
        {name: 'filter', mapping: 'tmt.mobie.blue.filter.value'},
        {name: 'disperser', mapping: 'tmt.mobie.blue.disperser.value'}
    ],

    proxy: {
        // Proxy for the command service REST API (from the common package - see extjs/packages/common)
        type: 'cmdsvc',

        // Function used by the proxy to query the server (and also to submit the values)
        // TODO: Is there a better way? Can this be extracted from the form automatically?
        getModelData: function(scope) {
            var filter = scope.data['filter'];
            var disperser = scope.data.disperser;
            if (!filter) filter = "";
            if (!disperser) disperser = "";

            return Ext.apply(
                {
                    config: {
                        tmt: {
                            mobie: {
                                blue: {
                                    filter: {value: filter},
                                    disperser: {value: disperser}
                                }
                            }
                        }
                    }
                }
            );
        }
    }
});
