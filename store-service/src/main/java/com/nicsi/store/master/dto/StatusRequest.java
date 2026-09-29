package com.nicsi.store.master.dto;

import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull Boolean active) {}
