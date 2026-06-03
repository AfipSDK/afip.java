[![Maven Central](https://img.shields.io/maven-central/v/com.afipsdk/afip-sdk-java.svg)](https://central.sonatype.com/artifact/com.afipsdk/afip-sdk-java)
[![Contributors](https://img.shields.io/github/contributors/afipsdk/afip.java.svg?color=orange)](https://github.com/afipsdk/afip.java/graphs/contributors)
[![License](https://img.shields.io/github/license/afipsdk/afip.java.svg?color=blue)](https://github.com/afipsdk/afip.java/blob/main/LICENSE)

Librería para conectarse a los Web Services de AFIP con Java

[Explorar documentación](https://docs.afipsdk.com) · [Comunidad Afip SDK](https://discord.gg/A6TuHEyAZm) · [Reportar un bug](https://github.com/afipsdk/afip.java/issues)

## Acerca del proyecto

Con más de 100k descargas en otros lenguajes, desde el 2017, Afip SDK es la plataforma preferida entre los desarrolladores para conectarse a los web services de ARCA. Esta librería integra facturación electrónica y otros servicios en aplicaciones Java.

## Versiones soportadas

| Java | Soporte |
|---|---|
| 8 | ✅ |
| 11 | ✅ |
| 17 | ✅ |
| 21 | ✅ |

## Instalación

**Maven**

```xml
<dependency>
    <groupId>com.afipsdk</groupId>
    <artifactId>afip-sdk-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle**

```gradle
implementation 'com.afipsdk:afip-sdk-java:1.0.0'
```

## Uso

```java
import com.afipsdk.Afip;
import com.afipsdk.model.AfipOptions;

AfipOptions options = new AfipOptions();
options.setCuit("TU_CUIT");
options.setProduction(false);
options.setAccessToken("TU_ACCESS_TOKEN");

Afip afip = new Afip(options);
```

### Facturación electrónica

```java
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// Obtener el último número de comprobante
int puntoDeVenta  = 1;
int tipoDeFactura = 6; // 6 = Factura B

int lastVoucher     = afip.electronicBilling().getLastVoucher(puntoDeVenta, tipoDeFactura);
int numeroDeFactura = lastVoucher + 1;

// Crear comprobante
String fecha = new SimpleDateFormat("yyyyMMdd").format(new Date());

Map<String, Object> alicuota = new HashMap<>();
alicuota.put("Id",      5);   // 5 = 21%
alicuota.put("BaseImp", 100.0);
alicuota.put("Importe", 21.0);

Map<String, Object> data = new HashMap<>();
data.put("CantReg",   1);
data.put("PtoVta",    puntoDeVenta);
data.put("CbteTipo",  tipoDeFactura);
data.put("Concepto",  1);     // 1 = Productos
data.put("DocTipo",   99);    // 99 = Consumidor Final
data.put("DocNro",    0);
data.put("CbteDesde", numeroDeFactura);
data.put("CbteHasta", numeroDeFactura);
data.put("CbteFch",   Integer.parseInt(fecha));
data.put("ImpTotal",  121.0);
data.put("ImpTotConc", 0);
data.put("ImpNeto",   100.0);
data.put("ImpOpEx",   0);
data.put("ImpIVA",    21.0);
data.put("ImpTrib",   0);
data.put("MonId",     "PES");
data.put("MonCotiz",  1);
data.put("CondicionIVAReceptorId", 5);
data.put("Iva", Arrays.asList(alicuota));

Map<String, Object> response = afip.electronicBilling().createVoucher(data);
System.out.println("CAE:         " + response.get("CAE"));
System.out.println("Vencimiento: " + response.get("CAEFchVto"));
```

## Documentación

[Explorar documentación](https://docs.afipsdk.com)

## Comunidad

[Comunidad Afip SDK](https://discord.gg/A6TuHEyAZm)

## Contacto

Soporte de Afip SDK - ayuda@afipsdk.com

https://github.com/afipsdk/afip.java

_Este software y sus desarrolladores no tienen ninguna relación con la AFIP._
