// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2026 Tony Germano

package org.openintegrationengine.smoketest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openintegrationengine.smoketest.SharedServer;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;

/** Saves a code template and a library that holds it, reads them back and removes them. */
@DisplayName("Code template libraries")
class CodeTemplateLibraryTest {

    @Test
    @DisplayName("saves, reads and removes a code template library")
    void codeTemplateLibrary() throws Exception {
        List<CodeTemplateLibrary> original = admin().getCodeTemplateLibraries(null, false);

        CodeTemplate template = CodeTemplate.getDefaultCodeTemplate("smokeTemplate" + suffix());
        CodeTemplateLibrary library = new CodeTemplateLibrary();
        library.setName("Smoke library " + suffix());
        // XStream rejects the JDK's immutable collections in request bodies.
        library.setCodeTemplates(new ArrayList<>(List.of(new CodeTemplate(template.getId()))));

        List<CodeTemplateLibrary> withNew = new ArrayList<>(original);
        withNew.add(library);
        try {
            assertTrue(admin().updateCodeTemplate(template, false), "updateCodeTemplate");
            assertTrue(admin().updateCodeTemplateLibraries(withNew, false), "updateCodeTemplateLibraries");

            CodeTemplateLibrary saved = admin().getCodeTemplateLibrary(library.getId(), true);
            assertNotNull(saved, "library " + library.getId());
            assertEquals(library.getName(), saved.getName());
            assertEquals(1, saved.getCodeTemplates().size(), "templates in the library");
            assertEquals(template.getCode(), saved.getCodeTemplates().get(0).getCode(), "template code");
        } finally {
            try {
                admin().updateCodeTemplateLibraries(original, true);
            } finally {
                admin().removeCodeTemplate(template.getId());
            }
        }

        assertTrue(admin().getCodeTemplates(null).stream().noneMatch(t -> t.getId().equals(template.getId())), "template after removal");
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Client admin() {
        return SharedServer.get().client();
    }
}
