# Extension compatibility

Custom plugins and connectors can opt into an OIE extension API version instead
of listing every compatible engine release. OIE currently provides extension
API **1.0.0**, versioned independently of the product release.

## Updating an extension

Build and test the extension against OIE. Add this element directly under the
root of each applicable `plugin.xml`, `source.xml`, and `destination.xml`:

```xml
<minExtensionApiVersion>1.0.0</minExtensionApiVersion>
```

Keep the extension's own `pluginVersion`. Existing dependencies, Java packages,
and plugin/connector implementations do not need to change for this mechanism.
The same archive can then install and load on subsequent OIE releases providing
a compatible extension API. Continue testing against supported engine releases;
metadata declares compatibility, it does not verify binary or runtime behavior.

When this element declares a non-null value, it replaces the `mirthVersion`
check. The engine's API version must be at least the requested version and have the same major
number: API 1.2.0 accepts a minimum of 1.0.0 or 1.2.0, but rejects 1.3.0 and
2.0.0. Versions must contain three nonnegative decimal integers separated by
periods, with no leading zeroes, suffixes, or wildcards; each component must fit
a Java signed integer. Surrounding whitespace is allowed. Empty or malformed
requirements reject the extension, even if `mirthVersion` matches.

Omit the element (or use XStream's explicit `class="null"` representation) to
retain the existing `mirthVersion` behavior: a comma-separated list of exact
engine releases, ignoring the engine's fourth build component.
Bundled extensions continue using this existing mechanism. Older engines that
predate this feature cannot read the new metadata field; retaining `mirthVersion`
alongside it does not provide backward compatibility with those engines.

## Maintaining the API version

`ExtensionCompatibility.API_VERSION` is a single compatibility contract covering
OIE's existing extension-facing classes across the server, shared models, and
Administrator client. It is deliberately separate from the product version and
does not require new JARs or relocated classes.

- Leave it unchanged for product releases that preserve the extension contract.
- Increase the minor version for compatible API additions; extensions using
  those additions must declare the new minimum.
- Increase the patch version for compatible contract fixes when extensions need
  to require that fix.
- Increase the major version for incompatible changes, including incompatible
  changes to dependencies exposed through the extension API. Extensions must be
  reviewed and updated before declaring support for that major version.

Extensions depending on implementation details outside that contract should
continue declaring exact engine releases. Compatibility checks run during
installation, launcher classpath construction, and engine metadata loading.
