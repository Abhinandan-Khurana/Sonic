package app.runout

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsProvider
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver

/**
 * The real framework transports legacy queries to DocumentsProvider's Bundle API.
 * Robolectric invokes the provider directly instead; mirror that framework adaptation
 * so DocumentFile queries run against the real test provider rather than a mock cursor.
 */
@Implements(ContentResolver::class)
class ModernDocumentsResolver : ShadowContentResolver() {
    @Implementation
    public override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val provider = getProvider(uri)
        return if (provider is DocumentsProvider) provider.query(uri, projection, null as Bundle?, null)
        else super.query(uri, projection, selection, selectionArgs, sortOrder)
    }
}
