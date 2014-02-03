
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
        getModelData: function(scope) {
            if (scope instanceof Assembly1.model.MobieBlue) {
                // doing a submit
                var filter = scope.data['filter'];
                var disperser = scope.data['disperser'];
                if (!filter && !disperser) return null; // nothing was changed
            }

            var obj = {
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
            };

            if (scope instanceof Assembly1.model.MobieBlue) {
                // only submit modified parts
                if (!filter) delete obj.config.tmt.mobie.blue.filter;
                if (!disperser) delete obj.config.tmt.mobie.blue.disperser;
                console.log("submit " + JSON.stringify(obj));
            } else {
                console.log("query " + JSON.stringify(obj));
            }

            return Ext.apply(obj);
        }
    }
});
