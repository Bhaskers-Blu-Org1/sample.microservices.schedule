/*
 * Copyright 2016 Microprofile.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microprofile.showcase.schedule.resources;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.microprofile.showcase.schedule.health.HealthCheckBean;
import io.microprofile.showcase.schedule.model.Schedule;
import io.microprofile.showcase.schedule.persistence.ScheduleDAO;


@ApplicationScoped
@Produces({MediaType.APPLICATION_JSON})
@Consumes(MediaType.APPLICATION_JSON)
@Path("/")
@Metered(name="io.microprofile.showcase.schedule.resources.ScheduleResource.Type.Metered",tags="app=schedule")
public class ScheduleResource {

    @Inject
    private ScheduleDAO scheduleDAO;
    private @Inject HealthCheckBean healthCheckBean;

    @POST
    @Counted(monotonic = true,tags="app=schedule")
    public Response add(final Schedule schedule) {
        final Schedule created = scheduleDAO.addSchedule(schedule);
        return Response.created(URI.create("/" + created.getId()))
                .entity(created)
                .build();
    }

    // For use as a k8s readinessProbe for this service
    @GET
    @Path("/nessProbe")
    @Produces(MediaType.TEXT_PLAIN)
    @Counted(monotonic = true,tags="app=schedule")
    public Response nessProbe() throws Exception {

        return Response.ok("schedule ready at " + Calendar.getInstance().getTime()).build();
    }

    @GET
    @Path("/{id}")
    public Response retrieve(@PathParam("id") final String id) {
        return scheduleDAO.findById(id)
                .map(schedule -> Response.ok(schedule).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Making returning of all slow schedules.
     * @return
     */
    @GET
    @Path("/all")
    @Timed
    @Metric(name="io.microprofile.showcase.schedule.resources.ScheduleResource.allSchedules.Metric",tags="app=schedule")
    public Response allSchedules() {
        final List<Schedule> allSchedules = scheduleDAO.getAllSchedules();
        final GenericEntity<List<Schedule>> entity = buildEntity(allSchedules);
        return Response.ok(entity).build();
    }

    @GET
    @Path("/allWithTimeout")
    @Timed
    @Metric(name="io.microprofile.showcase.schedule.resources.ScheduleResource.allSchedules.Metric",tags="app=schedule")
    @Timeout(3000)
    public Response allSchedulesWithTimeout() {
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok().build();
    }

    @GET
    @Path("/venue/{id}")
    @Counted(monotonic = true,tags="app=schedule")
    public Response allForVenue(@PathParam("id") final String id) {
        final List<Schedule> schedulesByVenue = scheduleDAO.findByVenue(id);
        final GenericEntity<List<Schedule>> entity = buildEntity(schedulesByVenue);
        return Response.ok(entity).build();
    }

    @GET
    @Path("/active/{dateTime}")
    @Counted(monotonic = true,tags="app=schedule")
    public Response activeAtDate(@PathParam("dateTime") final String dateTimeString) {
        final LocalDateTime dateTime = LocalDateTime.parse(dateTimeString);
        final List<Schedule> schedulesByDate = scheduleDAO.findByDate(dateTime.toLocalDate());
        final List<Schedule> activeAtTime = schedulesByDate.stream()
                .filter(schedule -> isTimeInSchedule(dateTime.toLocalTime(), schedule))
                .collect(Collectors.toList());
        final GenericEntity<List<Schedule>> entity = buildEntity(activeAtTime);
        return Response.ok(entity).build();
    }

    @GET
    @Path("/all/{date}")
    @Counted(monotonic = true,tags="app=schedule")
    public Response allForDay(@PathParam("date") final String dateString) {
        final LocalDate date = LocalDate.parse(dateString);
        final List<Schedule> schedulesByDate = scheduleDAO.findByDate(date);
        final GenericEntity<List<Schedule>> entity = buildEntity(schedulesByDate);
        return Response.ok(entity).build();
    }

    @DELETE
    @Counted(monotonic = true,tags="app=schedule")
    @Path("/{scheduleId}")
    public Response remove(@PathParam("scheduleId") final String scheduleId) {
        scheduleDAO.deleteSchedule(scheduleId);
        return Response.noContent().build();
    }
    
    @POST
    @Path("/updateHealthStatus")
    @Produces(TEXT_PLAIN)
    @Consumes(TEXT_PLAIN)
    @Counted(name="io.microprofile.showcase.schedule.resources.ScheduleResource.updateHealthStatus.monotonic.absolute",monotonic=true,absolute=true,tags="app=vote")
    public void updateHealthStatus(@QueryParam("isAppDown") Boolean isAppDown) {
    	healthCheckBean.setIsAppDown(isAppDown);
    }

    private GenericEntity<List<Schedule>> buildEntity(final List<Schedule> scheduleList) {
        return new GenericEntity<List<Schedule>>(scheduleList) {
        };
    }

    private boolean isTimeInSchedule(final LocalTime currentTime, final Schedule schedule) {
        final LocalTime scheduleStartTime = schedule.getStartTime();
        final LocalTime scheduleEndTime = scheduleStartTime.plus(schedule.getDuration());
        return scheduleStartTime.isBefore(currentTime) &&
                scheduleEndTime.isAfter(currentTime);
    }
}
