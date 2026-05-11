        v.onUpdate = update@{ normX, normY, dX, dY ->
            if (!InputInjector.isReady) return@update
            when (btn.joystickMode) {
                JoystickMode.STICK -> {
                    val tx = cx + normX * btn.joystickGameRadius
                    val ty = cy + normY * btn.joystickGameRadius
                    InputInjector.joystickUpdate(tx, ty)
                }
                JoystickMode.SWIPE_PAD -> {
                    if (dX != 0f || dY != 0f) {
                        val sens = btn.joystickGameRadius.toFloat()
                        InputInjector.swipePad(
                            cx.toFloat(), cy.toFloat(),
                            cx + dX * sens, cy + dY * sens
                        )
                    }
                }
            }
        }