package com.afipsdk.custom;

import com.afipsdk.Afip;
import com.afipsdk.model.AfipOptions;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Sandbox {

    public static void main(String[] args) {
        AfipOptions options = new AfipOptions();
        options.setAccessToken(System.getenv("AFIP_TOKEN"));
        options.setCuit(System.getenv("AFIP_CUIT"));
        options.setProduction(false);

        Afip afip = new Afip(options);

        int puntoDeVenta  = 1;
        int tipoDeFactura = 6; // 6 = Factura B

        int lastVoucher     = afip.electronicBilling().getLastVoucher(puntoDeVenta, tipoDeFactura);
        int numeroDeFactura = lastVoucher + 1;

        int concepto        = 1;     // 1 = Productos
        int tipoDeDocumento = 99;    // 99 = Consumidor Final
        int numeroDeDocumento = 0;

        String fecha = new SimpleDateFormat("yyyyMMdd").format(new Date());

        double importeGravado    = 100.0;
        double importeExentoIva  = 0.0;
        double importeIva        = 21.0;

        int condicionIvaReceptor = 5; // 5 = Consumidor Final

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("CantReg",   1);
        data.put("PtoVta",    puntoDeVenta);
        data.put("CbteTipo",  tipoDeFactura);
        data.put("Concepto",  concepto);
        data.put("DocTipo",   tipoDeDocumento);
        data.put("DocNro",    numeroDeDocumento);
        data.put("CbteDesde", numeroDeFactura);
        data.put("CbteHasta", numeroDeFactura);
        data.put("CbteFch",   Integer.parseInt(fecha));
        data.put("ImpTotal",  importeGravado + importeIva + importeExentoIva);
        data.put("ImpTotConc", 0);
        data.put("ImpNeto",   importeGravado);
        data.put("ImpOpEx",   importeExentoIva);
        data.put("ImpIVA",    importeIva);
        data.put("ImpTrib",   0);
        data.put("MonId",     "PES");
        data.put("MonCotiz",  1);
        data.put("CondicionIVAReceptorId", condicionIvaReceptor);

        // Solo para concepto 2 (Servicios) o 3 (Productos y Servicios)
        if (concepto == 2 || concepto == 3) {
            data.put("FchServDesde", 20191213);
            data.put("FchServHasta", 20191213);
            data.put("FchVtoPago",   20191213);
        }

        Map<String, Object> alicuota = new HashMap<String, Object>();
        alicuota.put("Id",      5); // 5 = 21%
        alicuota.put("BaseImp", importeGravado);
        alicuota.put("Importe", importeIva);
        data.put("Iva", Arrays.asList(alicuota));

        Map<String, Object> response = afip.electronicBilling().createVoucher(data);

        System.out.println("CAE:        " + response.get("CAE"));
        System.out.println("Vencimiento: " + response.get("CAEFchVto"));
    }
}
