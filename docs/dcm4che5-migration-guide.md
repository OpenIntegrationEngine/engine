# dcm4che5 Migration Guide

This guide covers migrating the OIE DICOM connector from the dcm4che2 backend to dcm4che5.

## Overview

The DICOM connector supports two backends selectable at server startup:

- **dcm4che2** (default) — The legacy backend wrapping `MirthDcmSnd`/`MirthDcmRcv`
- **dcm4che5** — The modern backend using dcm4che3 APIs (`Device`/`Connection`/`ApplicationEntity`)

Both backends expose identical behavior through the version-neutral `OieDicomSender`, `OieDicomReceiver`, and `OieDicomConverter` interfaces. Existing channel configurations (DICOM Listener and DICOM Sender properties) work with either backend without modification — with one exception noted in the [TLS section](#tls-configuration).

## Prerequisites

- Java 17 or later
- OIE build that includes `dicom-server-dcm5.jar` (the standard build produces it alongside `dicom-server-dcm2.jar`)

## Enabling dcm4che5

Edit `server/conf/mirth.properties` and uncomment or add:

```properties
dicom.library = dcm4che5
```

**Restart the server.** The property is read once at startup and cached.

To revert, change the value back to `dcm4che2` (or remove the line) and restart.

## What Changes

### Transparent (no action needed)

| Area | Behavior |
|------|----------|
| Channel properties | Listener and Sender UI properties work identically (see [Settings with no effect on dcm4che5](#settings-with-no-effect-on-dcm4che5) for two pure-tuning flags that are ignored) |
| Source map keys | Same keys populated: `localApplicationEntityTitle`, `remoteApplicationEntityTitle`, `localAddress`, `localPort`, `remoteAddress`, `remotePort` |
| DICOM object serialization | Byte-level output differs (different FMI implementation version UIDs), but all tag values are semantically equivalent |
| C-STORE, C-ECHO | Both work through the standard channel lifecycle |
| Storage commitment | `N-ACTION` → `N-EVENT-REPORT` flow works through `commit()` / `waitForStgCmtResult()` |
| Transfer syntaxes | All standard transfer syntaxes supported |

### Behavioral Differences

| Area | dcm4che2 | dcm4che5 |
|------|----------|----------|
| **Architecture** | Thin wrapper around monolithic `MirthDcmSnd`/`MirthDcmRcv` | Composed from `Device` + `Connection` + `ApplicationEntity` + service handlers |
| **Element names** | `"Patient's Name"` (DICOM PS3.6 style) | `"PatientName"` (keyword style) |
| **C-STORE dispatch** | Synchronous — `send()` blocks until receiver processes | Asynchronous — `send()` returns immediately, receiver processes on worker thread |
| **XML serialization** | Standard `TransformerFactory` | Hardened `TransformerFactory` with XXE protections (`ACCESS_EXTERNAL_DTD` and `ACCESS_EXTERNAL_STYLESHEET` disabled) |
| **Keystore type** | Inferred automatically from URL | Inferred from file extension (`.p12`/`.pfx` → PKCS12, otherwise JKS); can be set explicitly via `setKeyStoreType()` |

### Element Name Difference

If your channel logic inspects DICOM element names (not tag numbers), be aware that dcm4che5 uses keyword-style names:

```java
// dcm4che2: "Patient's Name"
// dcm4che5: "PatientName"

// Safe approach: use tag numbers, which are identical across backends
dicomObject.getString(Tag.PatientName)  // works on both
```

### Settings with no effect

A few Listener and Sender UI fields have no corresponding implementation on the dcm4che5 backend. The dcm4che2 backend behavior is preserved unchanged. A `WARN`-level log is emitted on channel start when any of these are set to a non-default value on dcm4che5:

| UI setting | Connector | Note |
|---|---|---|
| `bufSize` | Listener | File buffer size. dcm4che3 manages buffers internally; no equivalent API |
| `bufSize` | Sender | Transcoder buffer size. dcm4che3 manages buffers internally via `DataWriterAdapter` |
| `dest` (Store Received Objects in Directory) | Listener | Silently ignored on **both backends** — a long-standing upstream behavior. `MirthDcmRcv` streams DIMSE data directly to the channel and never consults this setting on either backend |

None of these affect data integrity. Messages still arrive and dispatch through the channel correctly. The `bufSize` flags are pure performance tuning; revert `dicom.library` to `dcm4che2` if the throughput difference matters for your deployment.

All other UI settings — TLS options, AE titles, timeouts, PDU lengths, transfer syntax selection, storage commitment, user identity, and priority — work identically on both backends.

### DICOMUtil API (user transformer scripts)

`DICOMUtil.byteArrayToDicomObject()` and `dicomObjectToByteArray()` now return / accept the version-neutral `OieDicomObject` type. For the vast majority of transformer scripts this change is invisible — Rhino's duck typing plus Object-type overloads on `OieDicomObject` mean existing calls keep working unchanged:

```javascript
// Existing scripts continue to work on default (dcm4che2) without changes:
var dcm = DICOMUtil.byteArrayToDicomObject(bytes, false);
dcm.getString(Tag.PatientName);                    // same method exists on OieDicomObject
dcm.putString(Tag.PatientName, VR.PN, "SMITH");    // Object-overload routes via VR.toString()
```

Only these specific patterns require a one-line change:

| Pattern | Change |
|---|---|
| `(DicomObject) DICOMUtil.byteArrayToDicomObject(...)` explicit cast | `(DicomObject) DICOMUtil.byteArrayToDicomObject(...).unwrap()` |
| `dcm instanceof DicomObject` | `dcm.unwrap() instanceof DicomObject` |
| Passing the result to a Java API that expects `org.dcm4che2.data.DicomObject` | Pass `dcm.unwrap()` instead |

The recommended version-neutral pattern for new scripts is to use string VR codes instead of library-specific constants:

```javascript
// Works identically on both backends — no dependency on VR class:
dcm.putString(Tag.PatientName, "PN", "SMITH");
```

## TLS Configuration

### Standard UI TLS Options

The DICOM connector UI offers three TLS cipher presets:

| UI Setting | Cipher Suite | Status |
|------------|-------------|--------|
| `aes` | `TLS_RSA_WITH_AES_128_CBC_SHA` | Disabled by default in current JDK security policies |
| `3des` | `SSL_RSA_WITH_3DES_EDE_CBC_SHA` | Disabled by default in current JDK security policies |
| `without` | `SSL_RSA_WITH_NULL_SHA` | Disabled by default in current JDK security policies |

These legacy cipher suites are disabled by current JDK security policies (Java 17 and later). This affects both backends equally. If your channels use TLS with any of these presets, they will fail with an `SSLHandshakeException` until you supply a custom cipher suite via a `DICOMConfiguration` (see below).

### Recommended: Custom Cipher Suites via DICOMConfiguration

For TLS on Java 21, implement a custom `DICOMConfiguration` that uses modern cipher suites:

**For dcm4che5:**

```java
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DICOMConfiguration;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomReceiver;
import com.mirth.connect.connectors.dimse.dicom.dcm5.Dcm5DicomSender;

public class MyDcm5DICOMConfiguration implements Dcm5DICOMConfiguration {

    private static final String[] MODERN_CIPHERS = {
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
    };

    @Override
    public void configureDcm5Sender(Dcm5DicomSender sender, DICOMDispatcher connector,
                                     DICOMDispatcherProperties props) throws Exception {
        // Apply standard config first
        DICOMConfigurationUtil.configureSender(sender, connector, props, protocols);

        // Override cipher suites with modern ones
        if (!"notls".equals(props.getTls())) {
            sender.setTlsCipherSuites(MODERN_CIPHERS);
        }
    }

    @Override
    public void configureDcm5Receiver(Dcm5DicomReceiver receiver, DICOMReceiver connector,
                                       DICOMReceiverProperties props) throws Exception {
        DICOMConfigurationUtil.configureReceiver(receiver, connector, props, protocols);

        if (!"notls".equals(props.getTls())) {
            receiver.setTlsCipherSuites(MODERN_CIPHERS);
        }
    }

    // ... remaining methods same as DefaultDcm5DICOMConfiguration
}
```

Then register your custom class. The `dicomConfigurationClass` is a Mirth **server configuration property** (stored in the database, not `mirth.properties`). Set it via the Mirth Administrator or REST API:

```
PUT /api/server/configuration/DICOM/dicomConfigurationClass
Body: com.example.MyDcm5DICOMConfiguration
```

And ensure dcm4che5 is enabled in `mirth.properties`:

```properties
dicom.library = dcm4che5
```

### Keystore Type

dcm4che5 requires an explicit keystore/truststore type. The default behavior infers this from the file extension:

| Extension | Inferred Type |
|-----------|--------------|
| `.p12`, `.pfx` | `PKCS12` |
| `.jks`, or anything else | `JKS` |

If your keystore URL does not have a standard extension (e.g., loaded from a classpath resource or HTTP URL), set the type explicitly in your custom `DICOMConfiguration`:

```java
sender.setKeyStoreType("PKCS12");
sender.setTrustStoreType("PKCS12");
```

## Custom DICOMConfiguration Migration

If you have a custom `DICOMConfiguration` implementation:

### If staying on dcm4che2

Change your class declaration from:

```java
// Before (no longer compiles — DICOMConfiguration is now version-neutral)
public class MyConfig implements DICOMConfiguration {
    void configureDcmSnd(MirthDcmSnd dcmsnd, ...) { ... }
    void configureDcmRcv(MirthDcmRcv dcmrcv, ...) { ... }
}
```

To:

```java
// After (one-line change — same method signatures)
public class MyConfig implements Dcm2DICOMConfiguration {
    void configureDcmSnd(MirthDcmSnd dcmsnd, ...) { ... }
    void configureDcmRcv(MirthDcmRcv dcmrcv, ...) { ... }
}
```

The `Dcm2DICOMConfiguration` interface has the same method signatures as the original pre-abstraction `DICOMConfiguration`. The bridge defaults handle the version-neutral interface methods automatically.

### If migrating to dcm4che5

Implement `Dcm5DICOMConfiguration` instead:

```java
public class MyConfig implements Dcm5DICOMConfiguration {

    @Override
    public void configureDcm5Sender(Dcm5DicomSender sender, DICOMDispatcher connector,
                                     DICOMDispatcherProperties props) throws Exception {
        // Use DICOMConfigurationUtil for standard property wiring
        DICOMConfigurationUtil.configureSender(sender, connector, props, protocols);

        // Add custom configuration
        sender.setTlsCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"});
    }

    @Override
    public void configureDcm5Receiver(Dcm5DicomReceiver receiver, DICOMReceiver connector,
                                       DICOMReceiverProperties props) throws Exception {
        DICOMConfigurationUtil.configureReceiver(receiver, connector, props, protocols);
    }

    @Override
    public Map<String, Object> getCStoreRequestInformation(Association association) {
        // association is org.dcm4che3.net.Association (not org.dcm4che2)
        Map<String, Object> map = new HashMap<>();
        map.put("calledAET", association.getCalledAET());
        return map;
    }

    @Override
    public Connection createDcm5Connection() {
        return new Connection();
    }

    // ...
}
```

Key differences from `Dcm2DICOMConfiguration`:

| | `Dcm2DICOMConfiguration` | `Dcm5DICOMConfiguration` |
|---|---|---|
| Receiver method | `configureDcmRcv(MirthDcmRcv, ...)` | `configureDcm5Receiver(Dcm5DicomReceiver, ...)` |
| Sender method | `configureDcmSnd(MirthDcmSnd, ...)` | `configureDcm5Sender(Dcm5DicomSender, ...)` |
| Association type | `org.dcm4che2.net.Association` | `org.dcm4che3.net.Association` |
| Network connection | `createLegacyNetworkConnection()` → `NetworkConnection` | `createDcm5Connection()` → `Connection` |

## JAR Architecture

The build produces three JARs:

| JAR | Contents | When Loaded |
|-----|----------|-------------|
| `dicom-server.jar` | Version-neutral interfaces, factory, connector classes | Always |
| `dicom-server-dcm2.jar` | `Dcm2DicomSender`, `Dcm2DicomReceiver`, `Dcm2DicomConverter`, MirthDcmSnd/Rcv | `dicom.library=dcm4che2` |
| `dicom-server-dcm5.jar` | `Dcm5DicomSender`, `Dcm5DicomReceiver`, `Dcm5DicomConverter` | `dicom.library=dcm4che5` |

The variant-based loading is controlled by the connector extension XML:

```xml
<library type="SERVER" path="dicom-server-dcm2.jar" variant="dicom.library:dcm4che2" />
<library type="SERVER" path="dicom-server-dcm5.jar" variant="dicom.library:dcm4che5" />
```

Only the JAR matching the configured library is loaded at runtime. The factory uses `Class.forName()` to avoid compile-time dependencies between the version-neutral code and either backend.

## Verification

After switching to dcm4che5:

1. **Check server logs for library detection:**
   ```
   DICOM library backend: DCM4CHE5
   ```

2. **Test a non-TLS DICOM channel:** Deploy a DICOM Listener on a test port. Use any DICOM SCU (e.g., dcm4che's `storescu` CLI tool, or a DICOM Sender channel) to send a C-STORE. Verify the message arrives and is processed.

3. **Test TLS channels (if applicable):** If using TLS, verify with a custom `DICOMConfiguration` that uses modern cipher suites (see [TLS section](#recommended-custom-cipher-suites-via-dicomconfiguration)).

4. **Verify source map:** Check that the source map in received messages contains the expected keys: `localApplicationEntityTitle`, `remoteApplicationEntityTitle`, `localAddress`, `localPort`, `remoteAddress`, `remotePort`.

5. **Test storage commitment (if applicable):** If any channels use storage commitment, verify the `N-ACTION` / `N-EVENT-REPORT` flow completes.

## Rollback

To revert to dcm4che2:

1. Edit `server/conf/mirth.properties`:
   ```properties
   dicom.library = dcm4che2
   ```
   (or remove the line entirely — dcm4che2 is the default)

2. If you changed `dicomConfigurationClass` (Mirth server configuration property) to a dcm5-specific implementation, revert it to your dcm2 implementation or remove the property via the Mirth Administrator or REST API.

3. Restart the server.

No channel configuration changes are needed — the version-neutral property model is shared.

## Troubleshooting

### `SSLHandshakeException: No appropriate protocol`

The configured cipher suite is disabled in your JDK's security policy. This affects both backends on current JDKs. Use a custom `DICOMConfiguration` with modern cipher suites (see [TLS section](#tls-configuration)).

### `IllegalStateException: keyStoreURL requires keyStoreType`

dcm4che5 requires an explicit keystore type. The default inference handles `.jks`, `.p12`, and `.pfx` extensions. If your keystore URL has a non-standard extension, call `setKeyStoreType()` explicitly in your custom `DICOMConfiguration`.

### `IncompatibleConnectionException` during sender `open()`

dcm4che5 requires TLS cipher suites to match on both the local and remote `Connection` objects. This is handled automatically by the `Dcm5DicomSender` implementation — if you see this error, ensure you're calling `setTlsCipherSuites()` (not the individual preset methods) and that `initTLS()` is called after all TLS setters.

### `NoPresentationContextException`

The receiver and sender must agree on at least one transfer syntax. Verify that both sides are configured with compatible transfer syntaxes (e.g., `1.2.840.10008.1.2` for Implicit VR Little Endian, `1.2.840.10008.1.2.1` for Explicit VR Little Endian).

### Element name mismatch in channel scripts

If your JavaScript/channel scripts compare element names as strings, they may break because dcm4che5 uses keyword-style names (`"PatientName"`) rather than dcm4che2's display names (`"Patient's Name"`). Use tag numbers (`Tag.PatientName` / `0x00100010`) instead.
