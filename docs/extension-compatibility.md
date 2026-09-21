# Writing and migrating extensions

OIE extensions can declare a minimum extension API version instead of listing
every compatible engine release. The current extension API is **1.0.0**. It is
versioned independently of both OIE releases and your extension's own releases.

This guide covers new plugins and connectors, migrating existing extensions,
and validating an archive before publishing it. Use an OIE build containing
this feature; a product version number alone does not identify support. The
current API version is defined in
[ExtensionCompatibility.API_VERSION](../server/src/main/java/com/mirth/connect/client/core/ExtensionCompatibility.java).

## Creating a new extension

1. Build against the oldest OIE API baseline you intend to support, using its
   existing extension interfaces and JARs. This feature introduces no new SDK, JAR layout,
   Java packages, or connector base classes. Keep OIE-provided classes out of
   your extension JARs. The current engine build uses JDK 17.
2. Choose your extension's own `pluginVersion`, such as `1.0.0`. For plugins
   **and connectors**, this is your release version; it need not match OIE.
3. Set `minExtensionApiVersion` to the lowest OIE extension API that provides
   every API your extension uses. For the initial contract, use `1.0.0`. Do not
   substitute the engine release number or a build-time engine-version token.
4. Declare the appropriate metadata and libraries as shown below, then follow
   [Packaging and validation](#packaging-and-validation).

Use compile-only/provided dependencies for OIE libraries. Depending on the
extension, these include `server-lib/mirth-client-core.jar` for shared models,
`server-lib/mirth-server.jar` for server hooks, `client-lib/mirth-client.jar`
for Administrator hooks, and the required Donkey or third-party libraries from
that OIE distribution. Declare and package any additional libraries your
extension needs using the existing library mechanism.

### Plugin metadata

A server-only plugin can use this `plugin.xml` template. Replace the example
identity, class name, and JAR name with your implementation. The class must
implement the relevant OIE plugin interface; this metadata does not generate
that implementation.

```xml
<pluginMetaData path="example">
    <name>Example Plugin</name>
    <author>Example Author</author>
    <pluginVersion>1.0.0</pluginVersion>
    <minExtensionApiVersion>1.0.0</minExtensionApiVersion>
    <description>An example server plugin.</description>
    <serverClasses>
        <string>com.example.oie.ExamplePlugin</string>
    </serverClasses>
    <library type="SERVER" path="example-server.jar"/>
</pluginMetaData>
```

For an Administrator component, also declare its `clientClasses` and `CLIENT`
libraries. Declare models needed by both sides in `SHARED` libraries. Preserve
other metadata required by your extension, such as API providers or migrations.
See the existing [Data Pruner descriptor](../server/src/main/resources/com/mirth/connect/plugins/datapruner/plugin.xml)
for the broader metadata structure; bundled descriptors still use release-based
compatibility, so use the API requirement shown above for an opted-in extension.

### Connector metadata

Connectors use `source.xml` and/or `destination.xml`, each with a
`connectorMetaData` root. A source descriptor template is:

```xml
<connectorMetaData path="exampleconnector">
    <name>Example Listener</name>
    <author>Example Author</author>
    <pluginVersion>1.0.0</pluginVersion>
    <minExtensionApiVersion>1.0.0</minExtensionApiVersion>
    <description>An example source connector.</description>
    <clientClassName>com.example.oie.ExampleListener</clientClassName>
    <serverClassName>com.example.oie.ExampleReceiver</serverClassName>
    <sharedClassName>com.example.oie.ExampleReceiverProperties</sharedClassName>
    <library type="CLIENT" path="exampleconnector-client.jar"/>
    <library type="SHARED" path="exampleconnector-shared.jar"/>
    <library type="SERVER" path="exampleconnector-server.jar"/>
    <transformers/>
    <protocol>example</protocol>
    <type>SOURCE</type>
</connectorMetaData>
```

For a destination, use `destination.xml`, set `type` to `DESTINATION`, and supply
its own name and sender, dispatcher, and properties classes. See the existing
[TCP source](../server/src/main/resources/com/mirth/connect/connectors/tcp/source.xml)
and [TCP destination](../server/src/main/resources/com/mirth/connect/connectors/tcp/destination.xml)
for their respective structures. If the connector also supplies a `plugin.xml`,
that descriptor needs its own compatibility declaration too.

## Migrating an existing extension

1. Build and test the extension against an OIE build containing this feature.
   Review its API and dependency usage. Extensions tied to unstable engine
   internals should retain exact release declarations until that coupling is
   addressed; changing metadata does not fix incompatible bytecode or behavior.
2. Update **every** applicable `plugin.xml`, `source.xml`, and `destination.xml`
   in the archive. Replace the engine-release compatibility declaration with a
   minimum extension API requirement, leaving your extension's other metadata
   intact.
3. Remove build filtering that replaces the API requirement with the current
   engine release. Keep `pluginVersion` under your own release policy. Existing
   dependencies, package names, and implementations require no change solely
   to opt into this mechanism.
4. Repackage the archive and complete [Packaging and validation](#packaging-and-validation).
   Subsequent compatible OIE releases do not require another descriptor update.

For example, change these fields:

```xml
<pluginVersion>2.3.0</pluginVersion>
<mirthVersion>4.5.2,4.6.0</mirthVersion>
```

To:

```xml
<pluginVersion>2.3.0</pluginVersion>
<minExtensionApiVersion>1.0.0</minExtensionApiVersion>
```

You may retain `mirthVersion`, but a non-null `minExtensionApiVersion` replaces
its check completely; the two are not combined. A future API requirement will
still reject the extension even if the legacy engine release matches. If any
other descriptor in the ZIP retains only a legacy requirement, it must still
match the engine release or installation of the entire archive is rejected.

### Supporting older engines

Engines predating this feature cannot read the new metadata field. Including
both fields does **not** make the same archive compatible with those engines.
If you need to support them, publish a separately identified legacy archive
that omits `minExtensionApiVersion` and retains its tested `mirthVersion` list.
Do not claim an older release supports API 1.0.0 merely because your extension
previously ran on it. To return an archive to legacy matching, remove the API
field and restore the tested release list; leaving the API field empty rejects
the extension.

## Packaging and validation

Keep the existing extension ZIP layout: an extension directory at the archive
root, containing its metadata and libraries. For the plugin template above,
include `example/`, `example/plugin.xml`, and `example/example-server.jar`.
The metadata `path` must match that directory, and each `library` path is
relative to it. Do not wrap the extension directory in another `extensions/`
directory. Library declarations are direct children of the metadata root,
not nested under a `libraries` element. Include directory entries before their
files, as normal recursive ZIP packaging does:

```bash
zip -r example-extension-1.0.0.zip example/
```

Before publishing an archive:

1. Inspect its final metadata after build filtering. Check all descriptors,
   class names, and library paths; do not validate only the source templates.
2. Install the ZIP through the Administrator's Extensions view on a test engine.
   Restart the server and Administrator to complete installation and load the
   new classes. Installation and startup both check compatibility.
3. Verify the extension is enabled and loads without compatibility, class, or
   dependency errors in the server logs. Exercise its actual functionality;
   for connectors, test message processing and the Administrator settings UI.
4. Test each engine release you support. When testing a newer release with a
   compatible API, reuse the **same archive** without changing its metadata.
   For an existing extension, also test upgrading an installation containing
   the previous extension version and any stored configuration it owns.

An incompatible requirement is rejected during installation before the archive
payload is extracted. Already installed incompatible extensions are excluded
from launcher library selection and engine metadata loading. Compatibility
metadata declares a requirement; it does not prove binary or runtime behavior.

## Compatibility rules

The engine API must be at least the requested version and have the same major
number. As a hypothetical later version, engine API `1.2.0` accepts a minimum of `1.0.0` or `1.2.0`,
but rejects `1.3.0` and `2.0.0`. These are **API versions**, not engine releases.

Versions must contain three nonnegative decimal integers separated by periods,
with no leading zeroes, suffixes, or wildcards; each component must fit a Java
signed integer. Surrounding whitespace is allowed. Empty or malformed values
reject the extension, even if `mirthVersion` matches.

Omitting the API requirement, or using XStream's explicit `class="null"`
representation, retains legacy matching: `mirthVersion` lists exact releases
separated by commas, ignoring the engine's fourth build component. Bundled
extensions continue using that existing mechanism.

## Maintaining the API version

`ExtensionCompatibility.API_VERSION` is one compatibility contract covering
OIE's existing extension-facing classes across the server, shared models, and
Administrator client. Engine maintainers should:

- Leave it unchanged for product releases that preserve the extension contract.
- Increase the minor version for compatible API additions. Extension authors
  using those additions must raise their minimum; existing extensions retain
  their earlier minimum.
- Increase the patch version for compatible contract fixes when extensions
  need to require that fix.
- Increase the major version for incompatible changes, including incompatible
  changes to dependencies exposed through the extension API. Extension authors
  must review, update, and test their code before declaring that major version.
