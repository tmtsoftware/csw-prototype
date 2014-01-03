
Ext.define('Assembly1.model.MobieBlue', {
    extend: 'Ext.data.Model',

    fields: [
        {name: 'filter', mapping: 'tmt.mobie.blue.filter.value'},
        {name: 'disperser', mapping: 'tmt.mobie.blue.disperser.value'}
    ],

    proxy : {
        type : 'ajax',
        actionMethods: {
            create : 'POST',
            read   : 'POST',
            update : 'POST'
        },
        api :{
            create : '/queue/submit',
            read : '/get',
            update : '/queue/submit'
        },
        reader: {
            type: 'json',
            root: 'config'
        },
        writer: {
            type: 'json',
            root: 'config',
            writeAllFields: true,
            nameProperty: 'mapping',
            expandData: true
        },
        doRequest: function(operation, callback, scope) {
            var writer  = this.getWriter(),
                request = this.buildRequest(operation);

            var filter = scope.data['filter'];
            var disperser = scope.data.disperser;
            if (!filter) filter = "";
            if (!disperser) disperser = "";

            var data = Ext.apply(
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

            var json = Ext.encode(data);

            if (operation.allowWrite()) {
                request = writer.write(request);
            }

            Ext.apply(request, {
                jsonData      : json,
                binary        : this.binary,
                headers       : this.headers,
                timeout       : this.timeout,
                scope         : this,
                callback      : this.createRequestCallback(request, operation, callback, scope),
                method        : this.getMethod(request),
                disableCaching: false // explicitly set it to false, ServerProxy handles caching
            });

            Ext.Ajax.request(request);

            return request;
        }
    }
});
