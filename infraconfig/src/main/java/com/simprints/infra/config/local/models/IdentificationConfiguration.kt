package com.simprints.infra.config.local.models

import com.simprints.infra.config.domain.models.IdentificationConfiguration
import com.simprints.infra.config.exceptions.InvalidProtobufEnumException

internal fun IdentificationConfiguration.toProto(): ProtoIdentificationConfiguration =
    ProtoIdentificationConfiguration.newBuilder()
        .setMaxNbOfReturnedCandidates(maxNbOfReturnedCandidates)
        .setPoolType(poolType.toProto())
        .build()

internal fun IdentificationConfiguration.PoolType.toProto(): ProtoIdentificationConfiguration.PoolType =
    when (this) {
        IdentificationConfiguration.PoolType.PROJECT -> ProtoIdentificationConfiguration.PoolType.PROJECT
        IdentificationConfiguration.PoolType.MODULE -> ProtoIdentificationConfiguration.PoolType.MODULE
        IdentificationConfiguration.PoolType.USER -> ProtoIdentificationConfiguration.PoolType.USER
    }

internal fun ProtoIdentificationConfiguration.toDomain(): IdentificationConfiguration =
    IdentificationConfiguration(
        maxNbOfReturnedCandidates,
        poolType.toDomain()
    )

internal fun ProtoIdentificationConfiguration.PoolType.toDomain(): IdentificationConfiguration.PoolType =
    when (this) {
        ProtoIdentificationConfiguration.PoolType.PROJECT -> IdentificationConfiguration.PoolType.PROJECT
        ProtoIdentificationConfiguration.PoolType.MODULE -> IdentificationConfiguration.PoolType.MODULE
        ProtoIdentificationConfiguration.PoolType.USER -> IdentificationConfiguration.PoolType.USER
        ProtoIdentificationConfiguration.PoolType.UNRECOGNIZED -> throw InvalidProtobufEnumException(
            "invalid PoolType $name"
        )
    }
