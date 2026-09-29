/*
 * Copyright (C) 2026 Daniel Georg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.scalableui.model;

import android.graphics.Rect;

import androidx.annotation.NonNull;

/**
 * Rewrites a variant's bounds after construction.
 *
 * <p>{@code Variant}'s geometry setters are {@code protected}, so this class lives in the
 * library's package (as {@link KeyFrameVariant} does). It is Java because same-package access
 * to {@code protected} members cannot be expressed in Kotlin.
 *
 * <p>{@code PanelTransitionCoordinator.updatePanelSurface} re-applies every panel a transition
 * did not touch from that panel's current variant, so a ball moved by a surface transaction
 * alone would jump back to its XML bounds on the next transition. Each frame therefore writes
 * the variant too.
 *
 * <p>Safe bounds follow the bounds: {@code applyState} writes both and an empty safe rect throws
 * on TaskPanel. {@code Variant.setBounds} stores the reference it is given, so the rect is copied.
 */
public final class PongVariantGeometry {

    private PongVariantGeometry() { }

    /** Write {@code bounds}, and the matching safe bounds, into {@code variant}. */
    public static void assignBounds(@NonNull Variant variant, @NonNull Rect bounds) {
        variant.setBounds(new Rect(bounds));
        variant.setSafeBounds(new Rect(bounds));
    }
}
