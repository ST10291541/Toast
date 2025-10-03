package vcmsa.projects.toastapplication.network

import retrofit2.Response
import retrofit2.http.*
import vcmsa.projects.toastapplication.CreateEventRequest
import vcmsa.projects.toastapplication.Event
import vcmsa.projects.toastapplication.RsvpResponse

interface ToastApiService {

    @POST("api/events")
    suspend fun createEvent(
        @Header("Authorization") token: String,
        @Body event: CreateEventRequest
    ): Response<Event>

    @GET("api/events")
    suspend fun getEvents(
        @Header("Authorization") token: String
    ): Response<List<Event>>

    @GET("api/events/{id}")
    suspend fun getEvent(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Event>

    @PATCH("api/events/{id}/drive-link")
    suspend fun updateDriveLink(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): Response<Event>

    // Submit poll / RSVP
    @POST("api/events/{eventId}/poll")
    suspend fun submitPoll(
        @Header("Authorization") token: String,
        @Path("eventId") eventId: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @POST("api/events/{id}/rsvp")
    suspend fun rsvpEvent(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body rsvp: RsvpResponse
    ): Response<Event>
}
