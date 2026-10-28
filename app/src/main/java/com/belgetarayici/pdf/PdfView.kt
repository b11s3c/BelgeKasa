package com.belgetarayici.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.LruCache
import android.util.Size
import java.io.Closeable
import java.io.File

/**
 * Platform renderer over decrypted PDF bytes. The bytes never touch shared storage: they live in
 * an anonymous memory file (API 30+) or in an app-private file that is unlinked as soon as it is
 * opened (API 28–29), so only the open descriptor can reach it.
 */
class PdfView(context: Context, bytes: ByteArray) : Closeable {
    private val pfd: ParcelFileDescriptor = openDescriptor(context, bytes)
    private val renderer = PdfRenderer(pfd)
    private val lock = Any()
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    val pageCount: Int = renderer.pageCount

    /** Page sizes in points, as displayed (rotation applied by the renderer). */
    val sizes: List<Size> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { Size(it.width, it.height) }
    }

    fun render(index: Int, widthPx: Int): Bitmap {
        val key = "$index:$widthPx"
        cache.get(key)?.let { return it }
        synchronized(lock) {
            renderer.openPage(index).use { page ->
                val w = widthPx.coerceIn(64, 4096)
                val h = (w.toFloat() * page.height / page.width).toInt().coerceIn(64, 6000)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(key, bmp)
                return bmp
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
        cache.evictAll()
    }

    companion object {
        private fun openDescriptor(context: Context, bytes: ByteArray): ParcelFileDescriptor {
            if (Build.VERSION.SDK_INT >= 30) {
                val fd = Os.memfd_create("belgekasa-pdf", 0)
                try {
                    var off = 0
                    while (off < bytes.size) off += Os.write(fd, bytes, off, bytes.size - off)
                    Os.lseek(fd, 0, OsConstants.SEEK_SET)
                    return ParcelFileDescriptor.dup(fd)
                } finally {
                    Os.close(fd)
                }
            }
            val dir = File(context.noBackupFilesDir, "render").apply { mkdirs() }
            val f = File.createTempFile("p", ".bin", dir)
            try {
                f.writeBytes(bytes)
                return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            } finally {
                f.delete()
            }
        }
    }
}
