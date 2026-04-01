package com.hippo.ehviewer.client.exception

class AddFavoritesRangeException(
    val completedCount: Int,
    cause: Throwable,
) : EhException(cause.message, cause)
