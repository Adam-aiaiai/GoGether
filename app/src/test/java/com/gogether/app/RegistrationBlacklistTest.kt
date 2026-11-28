package com.gogether.app

import com.gogether.app.core.data.DemoData
import com.gogether.app.core.model.*
import com.gogether.app.feature.registration.RegistrationEngine
import org.junit.Assert.*
import org.junit.Test

class RegistrationBlacklistTest {
    private val time=1800000000000L
    private fun engine():RegistrationEngine {
        val seed=DemoData.create(time)
        val e=seed.events.first().copy(startAt=time+600000,endAt=time+7200000,registrationDeadline=time+300000,capacity=2)
        val regs=listOf("alice","bob","carol","david").mapIndexed { index,id -> Registration("r$id",e.id,id,if(index<2)RegistrationStatus.CONFIRMED else RegistrationStatus.WAITLISTED,time-1000,time-1000,index.toLong()) }
        return RegistrationEngine(seed.copy(events=listOf(e),registrations=regs),{time},registrationBlocked={host,user->host=="alice"&&user=="carol"})
    }
    @Test fun cancellationSkipsBlacklistedWaiterAndKeepsHistory() {
        val e=engine();val id=e.snapshot.events.single().id;e.cancelRegistration("bob",id)
        assertEquals(RegistrationStatus.WAITLISTED,e.snapshot.registration(id,"carol")!!.status)
        assertEquals(RegistrationStatus.CONFIRMED,e.snapshot.registration(id,"david")!!.status)
    }
    @Test fun capacityIncreaseAlsoSkipsBlacklistedWaiter() {
        val e=engine();val event=e.snapshot.events.single()
        e.updateEvent("alice",event.id,EventDraft(event.title,event.category,event.description,event.startAt,event.location,event.city,3,event.registrationDeadline,endAt=event.endAt))
        assertEquals(RegistrationStatus.WAITLISTED,e.snapshot.registration(event.id,"carol")!!.status)
        assertEquals(RegistrationStatus.CONFIRMED,e.snapshot.registration(event.id,"david")!!.status)
    }
}
