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
package com.android.systemui.car.pingpong;

import com.android.car.scalableui.panel.TaskPanelController;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * The TaskPanel controllers' factory bindings: the ball, and a paddle that is an app.
 *
 * <p>Java, because {@code PanelControllerInitializer} injects the task-panel factory map with
 * the raw type {@code Map<Class<?>, Provider<TaskPanelController.Factory>>}. A Kotlin
 * {@code Factory<*>} compiles to {@code Factory<?>}, which Dagger keys as a different map that
 * the initializer never reads.
 */
@Module
public abstract class PongTaskPanelModule {

    @Binds
    @IntoMap
    @ClassKey(PongBallController.class)
    public abstract TaskPanelController.Factory bindPongBallControllerFactory(
            PongBallController.Factory factory);

    @Binds
    @IntoMap
    @ClassKey(PongPaddleTaskController.class)
    public abstract TaskPanelController.Factory bindPongPaddleTaskControllerFactory(
            PongPaddleTaskController.Factory factory);
}
