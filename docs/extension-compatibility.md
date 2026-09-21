# Writing and migrating extensions

OIE extensions can declare dependencies on the engine API and on other plugins.
The current extension API is **1.0.0**, versioned independently of OIE releases
and extension releases. Declaring an API requirement avoids listing every
compatible engine release.

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
3. Add an `engine-api` dependency with `minVersion` set to the lowest OIE
   extension API that provides every API your extension uses. For the initial
   contract, use `1.0.0`. Do not substitute the engine release number or a
   build-time engine-version token.
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
    <dependencies>
        <dependency type="engine-api" minVersion="1.0.0"/>
    </dependencies>
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
    <dependencies>
        <dependency type="engine-api" minVersion="1.0.0"/>
    </dependencies>
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

### Requiring another plugin

Add a `plugin` dependency alongside the engine requirement:

```xml
<dependencies>
    <dependency type="engine-api" minVersion="1.0.0"/>
    <dependency type="plugin" name="Provider Plugin" minVersion="2.1.0"/>
</dependencies>
```

The `name` must exactly match the provider's metadata `name`, including case;
it is not the directory `path`, class name, or JAR name. The version is checked
against that provider's **`pluginVersion`**, independently of the engine API.
Both plugins and connectors can require plugins; connectors cannot be named
as dependency providers. Keep a provider's name stable between releases.

All dependencies are required. A provider must be installed, enabled, satisfy
the version requirement, and satisfy its own dependencies. Install providers
first, or package the consumer and its providers together in one ZIP. OIE
does not download dependencies. Optional dependencies and version ranges are
not supported.

Dependencies control availability and compatibility. They do not change
class initialization or start order: existing weights and conditions still
apply. They do not guarantee that a provider's service has initialized or
started successfully. Code that uses another plugin's services must handle
its lifecycle and runtime failures. Declare dependencies on every descriptor
that needs the provider, including server, Administrator, shared-library, and
controller hooks.

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
<dependencies>
    <dependency type="engine-api" minVersion="1.0.0"/>
</dependencies>
```

Add `plugin` entries for any required plugins and test against the oldest
provider versions you declare. Merely adding metadata does not remove a
runtime dependency or make incompatible provider releases usable.

You may retain `mirthVersion`, but an `engine-api` dependency replaces its
check completely. A future API requirement still rejects the extension even
if the legacy engine release matches. Every descriptor in the ZIP must pass
its own checks or the entire archive is rejected.

If you already use `<minExtensionApiVersion>1.0.0</minExtensionApiVersion>`, it
remains supported. To migrate, remove that element and add the corresponding
`engine-api` dependency. Declaring both is invalid. Plugin dependencies can
also be added while retaining either the shorthand API requirement or legacy
release matching.

### Supporting older engines

Engines predating dependency-list support cannot read `dependencies`.
Including legacy fields does **not** make the same archive compatible with
those engines.
If you need to support them, publish a separately identified legacy archive
that omits `dependencies` and `minExtensionApiVersion` and retains its tested
`mirthVersion` list. Document and verify its plugin prerequisites separately.
Do not claim an older release supports API 1.0.0 merely because your extension
previously ran on it. To return an archive to legacy matching, remove the API
declaration and restore the tested release list; leaving an API requirement
empty rejects the extension.

## Packaging and validation

Keep the existing extension ZIP layout: an extension directory at the archive
root, containing its metadata and libraries. For the plugin template above,
include `example/`, `example/plugin.xml`, and `example/example-server.jar`.
The metadata `path` must match that directory, and each `library` path is
relative to it. Keep package-directory spelling and case unchanged when
updating or uninstalling an extension. Archive paths that differ only by case
are rejected to avoid filesystem-dependent results. Do not wrap the extension directory in another `extensions/`
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
5. For plugin dependencies, test a missing or disabled provider, a provider
   below the minimum, and a provider with a different major version. Check
   transitive failures, dependency cycles, and recovery after correcting the
   declarations or enabling compatible providers. Verify a provider update,
   disable, or removal cannot break an enabled consumer with satisfied
   dependencies.

An incompatible requirement is rejected during installation before the archive
payload is extracted. Installation checks the complete inventory for the next
startup: installed packages, pending removals, staged installations, and every
descriptor in the new ZIP. Descriptor order within the ZIP does not determine
dependency availability. Pending installs replace the corresponding package
directory, including its descriptors.

A provider update, disable, or removal is rejected if it would break the
dependencies of an enabled consumer whose requirements currently pass. Disable
or uninstall consumers first when intentionally removing their providers.
Restart the server and Administrator to apply staged changes; staging does
not unload classes from a running process.

At startup, dependency checks run before extension libraries are admitted and
before metadata is made available to the engine. Unsatisfied extensions are
excluded, with diagnostics identifying the requirement. Disabled consumers
still need valid declarations and engine compatibility, but their plugin
requirements are checked when they are enabled. Compatibility metadata
declares a requirement; it does not prove binary or runtime behavior.

## Compatibility rules

For either dependency type, the available version must be at least `minVersion`
and have the same major number. For example, engine API `1.2.0` accepts a
minimum of `1.0.0` or `1.2.0`, but rejects `1.3.0` and `2.0.0`. A plugin
requirement of `2.1.0` accepts provider release `2.2.0`, but rejects `2.0.0`
and `3.0.0`. Plugin authors should use major version changes to signal
incompatible changes to their published extension interfaces.

Versions must contain three nonnegative decimal integers separated by periods,
with no leading zeroes, suffixes, or wildcards; each component must fit a Java
signed integer. Surrounding whitespace is allowed. Empty or malformed values
reject the requirement, even if `mirthVersion` matches. A required provider's
`pluginVersion` must use this format too; the engine does not infer compatibility
from arbitrary release labels.

Only `engine-api` and `plugin` dependency types are supported, and both require
`minVersion`. Plugin dependencies also require `name`; engine dependencies do
not use a name. Unknown types, malformed declarations, duplicate requirements,
self-dependencies, cycles, and ambiguous duplicate provider names are rejected.

The engine check uses the `engine-api` dependency if present. Otherwise it uses
a non-null `minExtensionApiVersion`, then legacy `mirthVersion` matching when
the shorthand is absent or explicitly null. A list containing only plugin
dependencies, or an empty list, never skips the engine check. Legacy matching
accepts exact comma-separated engine releases, ignoring the engine's fourth
build component. Existing descriptors without the new declarations keep this
behavior.

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
