package io.fairspace.ceres.pid.service

import io.fairspace.ceres.pid.TestData
import io.fairspace.ceres.pid.TestData.path1
import io.fairspace.ceres.pid.model.Pid
import io.fairspace.ceres.pid.repository.PidRepository
import junit.framework.Assert.assertEquals
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import java.util.*

@DataJpaTest
@RunWith(SpringRunner::class)
@SpringBootTest
@DirtiesContext

class PidServiceTest {
    @Autowired
    lateinit var pidRepository: PidRepository

    lateinit var pidService: PidService

    lateinit var saved_pid1: Pid
    lateinit var saved_pid2: Pid
    lateinit var saved_pid3: Pid

    @Before
    fun setUp() {
        pidService = PidService(pidRepository)
        saved_pid1 = pidService.add(TestData.path1)
        saved_pid2 = pidService.add(TestData.path2)
        saved_pid3 = pidService.add(TestData.path3)
    }

    @Test
    fun can_find_entity_by_uuid() {
        val foundPath = pidService.findById(saved_pid1.uuid)
            assert(foundPath.uuid.equals(saved_pid1.uuid))
    }

    @Test
    fun entity_uuid_has_been_changed_on_save() {
        val foundPath = pidService.findById(saved_pid1.uuid)
        Assert.assertNotEquals(foundPath.uuid,path1.uuid)
    }

    @Test
    fun can_find_entity_by_uri() {
        val foundPath = pidService.findByUri(TestData.path1.uri)
        assertEquals(foundPath.uuid, saved_pid1.uuid)
    }

    @Test
    fun can_delete_entity() {
        assert(pidRepository.findById(saved_pid3.uuid).isPresent())
        pidService.delete(saved_pid3.uuid)
        Assert.assertFalse(pidRepository.findById(saved_pid3.uuid).isPresent())
    }

    @Test
    fun can_save_entity () {
        val test_uri = "https://workspace.test.fairway.app/iri/collections/789/foo/bat"
        val test_uuid = UUID.fromString("af4bec86-5297-4521-89d7-13ca579f6fb2")

        val testPid: Pid = Pid( uuid = test_uuid, uri = test_uri )

        Assert.assertNull(pidRepository.findByUri(test_uri))
        val addedPath = pidService.add(testPid)
        val foundPid: Pid? = pidRepository.findByUri(test_uri)
        Assert.assertNotNull(foundPid)
        assert(addedPath!!.uuid.equals(foundPid!!.uuid))
    }
}
