@file:JvmName("ImageHashKt")

package com.hippo.ehviewer.jni

import android.graphics.Bitmap

external fun getDHash(bitmap: Bitmap): Long
