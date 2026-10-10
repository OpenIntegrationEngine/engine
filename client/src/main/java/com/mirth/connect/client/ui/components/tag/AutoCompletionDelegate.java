/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.ui.components.tag;

import java.util.function.Consumer;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;

public class AutoCompletionDelegate extends AutoCompletion {
    private Consumer<TagCompletion> onAccept;

    public AutoCompletionDelegate(CompletionProvider provider, Consumer<TagCompletion> onAccept) {
        super(provider);
        this.onAccept = onAccept;
        setHideOnNoText(false);
    }

    @Override
    public boolean hidePopupWindow() {
        return super.hidePopupWindow();
    }

    /**
     * The selected completion becomes a tag rather than editor text, so the default insertion (and
     * the caret arithmetic that goes with it) is deliberately skipped. This covers both accept
     * paths, since the final insertCompletion(Completion) delegates here.
     */
    @Override
    protected void insertCompletion(Completion completion, boolean typedParamListStartChar) {
        hidePopupWindow();
        onAccept.accept((TagCompletion) completion);
    }
}
