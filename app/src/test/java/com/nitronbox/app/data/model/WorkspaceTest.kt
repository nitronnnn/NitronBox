package com.nitronbox.app.data.model

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTest {
    @Test
    fun `valid workspace has no validation errors`() {
        val workspace = Workspace(
            name = "Engineering",
            route = ModelRoute(ModelTarget("provider", "discovered-model")),
        )

        assertTrue(workspace.validate().isEmpty())
    }

    @Test
    fun `invalid generation and context settings are reported together`() {
        val workspace = Workspace(
            name = "",
            route = ModelRoute(ModelTarget("", "")),
            generation = GenerationSettings(temperature = 3f, topP = -1f),
            contextPolicy = ContextPolicy(maxInputTokens = 400, reservedOutputTokens = 400),
        )

        val errors = workspace.validate()
        assertTrue(WorkspaceValidationError.EmptyName in errors)
        assertTrue(WorkspaceValidationError.InvalidPrimaryRoute in errors)
        assertTrue(WorkspaceValidationError.InvalidTemperature in errors)
        assertTrue(WorkspaceValidationError.InvalidTopP in errors)
        assertTrue(WorkspaceValidationError.ContextWindowTooSmall in errors)
        assertTrue(WorkspaceValidationError.OutputReserveExceedsContext in errors)
    }
}