package com.flatide.floodgate.core

object FloodgateConstants {
    const val META_SOURCE_TABLE_FOR_API = "meta.source.tableForAPI"
    const val META_SOURCE_TABLE_FOR_FLOW = "meta.source.tableForFlow"
    const val META_SOURCE_TABLE_FOR_DATASOURCE = "meta.source.tableForDatasource"
    const val META_SOURCE_TABLE_FOR_TEMPLATE = "meta.source.tableForTemplate"
    const val META_SOURCE_TABLE_FOR_META_HISTORY = "meta.source.tableForMetaHistory"
    const val META_SOURCE_BACKUP_FOLDER = "meta.source.backupFolder"
    const val META_SOURCE_BACKUP_RULE_FOR_FLOW = "meta.source.backupRuleForFlow"
    const val META_SOURCE_BACKUP_RULE_FOR_DATASOURCE = "meta.source.backupRuleForDatasource"

    const val CHANNEL_SPOOLING_FOLDER = "channel.spooling.folder"
    const val CHANNEL_PAYLOAD_FOLDER = "channel.payload.folder"
    const val CHANNEL_LOG_TABLE_FOR_API = "channel.log.tableForAPI"
    const val CHANNEL_LOG_TABLE_FOR_FLOW = "channel.log.tableForFlow"

    const val TRANSFER_BUFFER_QUEUE_CAPACITY = "transfer.bufferQueueCapacity"
    const val TRANSFER_CHUNK_SIZE = "transfer.chunkSize"
    const val TRANSFER_MAX_CONCURRENT = "transfer.maxConcurrentTransfers"
    const val TRANSFER_TIMEOUT_SECONDS = "transfer.timeoutSeconds"
}
