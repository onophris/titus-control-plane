/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.titus.runtime.connector.jobmanager.replicator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.netflix.titus.api.jobmanager.TaskAttributes;
import com.netflix.titus.api.jobmanager.model.CallMetadata;
import com.netflix.titus.api.jobmanager.model.job.Capacity;
import com.netflix.titus.api.jobmanager.model.job.Job;
import com.netflix.titus.api.jobmanager.model.job.JobDescriptor;
import com.netflix.titus.api.jobmanager.model.job.JobState;
import com.netflix.titus.api.jobmanager.model.job.Task;
import com.netflix.titus.api.jobmanager.model.job.TaskState;
import com.netflix.titus.api.jobmanager.model.job.event.JobManagerEvent;
import com.netflix.titus.api.jobmanager.model.job.event.JobUpdateEvent;
import com.netflix.titus.api.jobmanager.model.job.event.TaskUpdateEvent;
import com.netflix.titus.common.runtime.TitusRuntime;
import com.netflix.titus.common.runtime.TitusRuntimes;
import com.netflix.titus.common.util.tuple.Pair;
import com.netflix.titus.runtime.connector.common.replicator.DataReplicatorMetrics;
import com.netflix.titus.runtime.connector.common.replicator.ReplicatorEvent;
import com.netflix.titus.runtime.jobmanager.gateway.JobServiceGateway;
import com.netflix.titus.runtime.connector.jobmanager.JobSnapshot;
import com.netflix.titus.testkit.model.job.JobComponentStub;
import com.netflix.titus.testkit.model.job.JobDescriptorGenerator;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Test;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GrpcJobReplicatorEventStreamTest {

    private static final String SERVICE_JOB = "serviceJob";
    private static final String BATCH_JOB = "batchJob";

    private static final int SERVICE_DESIRED = 5;
    private static final int BATCH_DESIRED = 1;

    private final TitusRuntime titusRuntime = TitusRuntimes.test();

    private final JobComponentStub dataGenerator = new JobComponentStub(titusRuntime);

    private final JobServiceGateway client = mock(JobServiceGateway.class);

    @Before
    public void setUp() {
        dataGenerator.addJobTemplate(SERVICE_JOB, JobDescriptorGenerator.serviceJobDescriptors()
                .map(jd -> jd.but(d -> d.getExtensions().toBuilder().withCapacity(Capacity.newBuilder().withDesired(SERVICE_DESIRED).withMax(10).build())))
                .cast(JobDescriptor.class)
        );

        dataGenerator.addJobTemplate(BATCH_JOB, JobDescriptorGenerator.batchJobDescriptors()
                .map(jd -> jd.but(d -> d.getExtensions().toBuilder().withSize(BATCH_DESIRED)))
                .cast(JobDescriptor.class)
        );
    }

    @Test
    public void testCacheBootstrap() {
        dataGenerator.creteMultipleJobsAndTasks(SERVICE_JOB, BATCH_JOB);

        newConnectVerifier()
                .assertNext(initialReplicatorEvent -> {
                    assertThat(initialReplicatorEvent).isNotNull();

                    JobSnapshot cache = initialReplicatorEvent.getSnapshot();
                    assertThat(cache.getJobs()).hasSize(2);
                    assertThat(cache.getTasks()).hasSize(SERVICE_DESIRED + BATCH_DESIRED);
                })

                .thenCancel()
                .verify();
    }

    @Test
    public void testCacheJobUpdate() {
        Job job = dataGenerator.createJob(SERVICE_JOB);

        newConnectVerifier()
                .assertNext(next -> assertThat(next.getSnapshot().getJobs().get(0).getStatus().getState()).isEqualTo(JobState.Accepted))
                .then(() -> dataGenerator.moveJobToKillInitiatedState(job))
                .assertNext(next -> assertThat(next.getSnapshot().getJobs().get(0).getStatus().getState()).isEqualTo(JobState.KillInitiated))
                .thenCancel()
                .verify();
    }

    @Test
    public void testCacheJobRemove() {
        Job job = dataGenerator.createJob(SERVICE_JOB);
        dataGenerator.moveJobToKillInitiatedState(job);

        newConnectVerifier()
                .assertNext(next -> assertThat(next.getSnapshot().getJobs().get(0).getStatus().getState()).isEqualTo(JobState.KillInitiated))
                .then(() -> dataGenerator.finishJob(job))
                .assertNext(next -> assertThat(next.getSnapshot().getJobs()).isEmpty())

                .thenCancel()
                .verify();
    }

    @Test
    public void testCacheTaskUpdate() {
        Pair<Job, List<Task>> pair = dataGenerator.createJobAndTasks(BATCH_JOB);
        Task task = pair.getRight().get(0);

        newConnectVerifier()
                .assertNext(next -> assertThat(next.getSnapshot().getTasks().get(0).getStatus().getState()).isEqualTo(TaskState.Accepted))
                .then(() -> dataGenerator.moveTaskToState(task, TaskState.Launched))
                .assertNext(next -> assertThat(next.getSnapshot().getTasks().get(0).getStatus().getState()).isEqualTo(TaskState.Launched))

                .thenCancel()
                .verify();
    }

    @Test
    public void testCacheTaskMove() {
        Pair<Job, List<Task>> pair = dataGenerator.createJobAndTasks(SERVICE_JOB);
        Job target = dataGenerator.createJob(SERVICE_JOB);
        Task task = pair.getRight().get(0);
        String sourceJobId = pair.getLeft().getId();
        String targetJobId = target.getId();
        List<ReplicatorEvent<JobSnapshot, JobManagerEvent<?>>> events = new ArrayList<>();

        newConnectVerifier()
                .assertNext(next -> assertThat(next.getSnapshot().getTasks())
                        .allSatisfy(t -> assertThat(t.getStatus().getState()).isEqualTo(TaskState.Accepted)))
                .then(() -> dataGenerator.moveTaskToState(task, TaskState.Started))
                .assertNext(next -> {
                    JobSnapshot snapshot = next.getSnapshot();
                    Optional<Pair<Job<?>, Task>> taskOpt = snapshot.findTaskById(task.getId());
                    assertThat(taskOpt).isPresent();
                    assertThat(taskOpt.get().getRight().getStatus().getState()).isEqualTo(TaskState.Started);
                    assertThat(snapshot.getTasks(sourceJobId))
                            .haveExactly(1, new Condition<>(t -> t.getId().equals(task.getId()), "found the task"));
                })
                .then(() -> dataGenerator.getJobOperations()
                        .moveServiceTask(sourceJobId, targetJobId, task.getId(), CallMetadata.newBuilder().withCallerId("Test").withCallReason("testing").build())
                        .test()
                        .awaitTerminalEvent()
                        .assertNoErrors()
                )
                .recordWith(() -> events)
                .thenConsumeWhile(next -> {
                    JobManagerEvent<?> trigger = next.getTrigger();
                    if (!(trigger instanceof TaskUpdateEvent)) {
                        return true;
                    }
                    TaskUpdateEvent taskUpdateEvent = (TaskUpdateEvent) trigger;
                    return !taskUpdateEvent.isMovedFromAnotherJob();
                })
                .thenCancel()
                .verify();

        assertThat(events).hasSize(3);
        events.stream().map(ReplicatorEvent::getTrigger).forEach(jobManagerEvent -> {
            if (jobManagerEvent instanceof JobUpdateEvent) {
                JobUpdateEvent jobUpdateEvent = (JobUpdateEvent) jobManagerEvent;
                String eventJobId = jobUpdateEvent.getCurrent().getId();
                assertThat(eventJobId).isIn(sourceJobId, targetJobId);
            } else if (jobManagerEvent instanceof TaskUpdateEvent) {
                TaskUpdateEvent taskUpdateEvent = (TaskUpdateEvent) jobManagerEvent;
                assertThat(taskUpdateEvent.isMovedFromAnotherJob()).isTrue();
                assertThat(taskUpdateEvent.getCurrentJob().getId()).isEqualTo(targetJobId);
                assertThat(taskUpdateEvent.getCurrent().getJobId()).isEqualTo(targetJobId);
                assertThat(taskUpdateEvent.getCurrent().getTaskContext().get(TaskAttributes.TASK_ATTRIBUTES_MOVED_FROM_JOB))
                        .isEqualTo(sourceJobId);
            } else {
                fail("Unexpected event type: %s", jobManagerEvent);
            }
        });
    }

    @Test
    public void testCacheTaskRemove() {
        Pair<Job, List<Task>> pair = dataGenerator.createJobAndTasks(BATCH_JOB);
        Task task = pair.getRight().get(0);

        newConnectVerifier()
                .assertNext(next -> assertThat(next.getSnapshot().getTasks().get(0).getStatus().getState()).isEqualTo(TaskState.Accepted))
                .then(() -> dataGenerator.moveTaskToState(task, TaskState.Finished))
                .assertNext(next -> assertThat(next.getSnapshot().getTasks()).isEmpty())

                .thenCancel()
                .verify();
    }

    @Test
    public void testReemit() {
        dataGenerator.creteMultipleJobsAndTasks(SERVICE_JOB, BATCH_JOB);

        newConnectVerifier()
                .expectNextCount(1)
                .expectNoEvent(Duration.ofMillis(GrpcJobReplicatorEventStream.LATENCY_REPORT_INTERVAL_MS))
                .expectNextCount(1)

                .thenCancel()
                .verify();
    }

    private GrpcJobReplicatorEventStream newStream() {
        when(client.observeJobs(any())).thenReturn(dataGenerator.grpcObserveJobs(true));
        return new GrpcJobReplicatorEventStream(client, new DataReplicatorMetrics("test", titusRuntime), titusRuntime, Schedulers.parallel());
    }

    private StepVerifier.FirstStep<ReplicatorEvent<JobSnapshot, JobManagerEvent<?>>> newConnectVerifier() {
        return StepVerifier.withVirtualTime(() -> newStream().connect().log());
    }
}
