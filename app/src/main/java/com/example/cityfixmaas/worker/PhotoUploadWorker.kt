package com.example.cityfixmaas.worker

import com.google.firebase.auth.FirebaseAuth
import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.cityfixmaas.data.media.ImageSize
import com.example.cityfixmaas.data.media.uriToBase64Jpeg
import com.example.cityfixmaas.data.model.MediaContent
import com.example.cityfixmaas.data.model.MediaPhase
import com.example.cityfixmaas.data.model.MediaRef
import com.example.cityfixmaas.data.model.Ticket
import com.example.cityfixmaas.data.model.UploadState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Uploads the photos of one ticket in the background, only when there is internet.
 *
 * When a ticket is created, its photos are copied into the app's own storage and listed in the
 * ticket as PENDING. This worker then, for each pending photo:
 * 1. compresses it (1280 px photo + 200 px thumbnail) and encodes it as Base64,
 * 2. saves the photo in tickets/{id}/media/{mediaId},
 * 3. marks it DONE in the ticket and adds the thumbnail (also to publicTickets for the first photo),
 * 4. deletes the local copy.
 * Only photos added on this phone are touched: a ticket can also have pending photos of another
 * citizen (grouped reports) or of an operator, and those are uploaded by their own phones.
 * If something fails (e.g. the connection drops), WorkManager runs it again later.
 */
class PhotoUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val db = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result {
        val ticketId = inputData.getString(KEY_TICKET_ID) ?: return Result.failure()
        return try {
            val ticket = db.collection("tickets").document(ticketId).get().await()
                .toObject<Ticket>() ?: return Result.failure()
            val me = FirebaseAuth.getInstance().currentUser?.uid
            for (ref in ticket.media.filter { it.uploadState == UploadState.PENDING && it.addedBy == me }) {
                val file = localFile(applicationContext, ref.id)
                // The local copy is gone (e.g. app data cleared): this photo can never be uploaded.
                if (file.exists()) upload(ticket, ref, file) else markState(ticketId, ref.id, UploadState.FAILED, null)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun upload(ticket: Ticket, ref: MediaRef, file: File) {
        val uri = Uri.fromFile(file)
        val photo = uriToBase64Jpeg(applicationContext, uri, ImageSize.PHOTO)
        val thumbnail = uriToBase64Jpeg(applicationContext, uri, ImageSize.THUMBNAIL)

        val content = MediaContent(ref.id, ticket.id, photo, ref.phase, ref.createdAt)
        db.collection("tickets").document(ticket.id).collection("media").document(ref.id).set(content).await()

        markState(ticket.id, ref.id, UploadState.DONE, thumbnail)

        // The public list shows the first photo of the report as its thumbnail.
        val firstReportPhoto = ticket.media.firstOrNull { it.phase == MediaPhase.REPORT }
        if (ticket.publicScope && firstReportPhoto?.id == ref.id) {
            db.collection("publicTickets").document(ticket.id).update("thumbnailBase64", thumbnail).await()
        }
        file.delete()
    }

    /**
     * Changes the state (and thumbnail) of one photo in the ticket's media list. A transaction,
     * because two phones may update the same list at the same time (grouped reports).
     */
    private suspend fun markState(ticketId: String, mediaId: String, state: UploadState, thumbnail: String?) {
        val ticketRef = db.collection("tickets").document(ticketId)
        db.runTransaction { transaction ->
            val ticket = transaction.get(ticketRef).toObject<Ticket>() ?: return@runTransaction
            val media = ticket.media.map {
                if (it.id == mediaId) it.copy(uploadState = state, thumbnailBase64 = thumbnail ?: it.thumbnailBase64) else it
            }
            transaction.update(ticketRef, "media", media)
        }.await()
    }

    companion object {
        private const val KEY_TICKET_ID = "ticketId"

        /** Where a photo waits until it is uploaded. */
        fun localFile(context: Context, mediaId: String) = File(context.filesDir, "photos/$mediaId.jpg")

        /**
         * Copies a picked or captured photo into app storage right away: photo picker URIs stop
         * working after a while, and the worker may run much later.
         */
        fun saveLocally(context: Context, uri: Uri, mediaId: String) {
            val file = localFile(context, mediaId)
            file.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not read the photo" }
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }

        /** Schedules the upload of a ticket's pending photos for when the device is online. */
        fun enqueue(context: Context, ticketId: String) {
            val request = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setInputData(workDataOf(KEY_TICKET_ID to ticketId))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
