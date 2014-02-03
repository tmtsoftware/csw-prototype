// Filter Store (TODO: make remote)

Ext.define('Assembly1.store.Filters', {
    extend:  'Ext.data.ArrayStore',
    fields: ['name'],
    data: [
        ["None"],
        ["g_G0301"],
        ["r_G0303"],
        ["i_G0302"],
        ["z_G0304"],
        ["Z_G0322"],
        ["Y_G0323"],
        ["GG455_G0305"],
        ["OG515_G0306"],
        ["RG610_G0307"],
        ["CaT_G0309"],
        ["Ha_G0310"],
        ["HaC_G0311"],
        ["DS920_G0312"],
        ["SII_G0317"],
        ["OIII_G0318"],
        ["OIIIC_G0319"],
        ["HeII_G0320"],
        ["HeIIC_G0321"],
        ["HartmannA_G0313 + r_G0303"],
        ["HartmannB_G0314 + r_G0303"],
        ["g_G0301 + GG455_G0305"],
        ["g_G0301 + OG515_G0306"],
        ["r_G0303 + RG610_G0307"],
        ["i_G0302 + CaT_G0309"],
        ["z_G0304 + CaT_G0309"],
        ["u_G0308"]
    ]
});
