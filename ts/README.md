Time Service
=============

This project implements the CSW Time Service based on Java 8 java.time and Akka.
Time Service provides basic time access and a neutral API around the Akka scheduling routines.
Accessing the time API does not require an actor, but the scheduling routines
are assumed to be actors. The scheduling trait is TimeServiceScheduler.

Time Access
---------------

Time Access can be used by importing TimeServce._
It includes time and date/time routines for local time, UTC, TAI, and local time in Hawaii.

TimeServiceScheduler
----------------

The TimeServiceScheduler trait adds the two methods `scheduleOnce(startTime, receiver, message)`
and `schedule(startTime, period, receiver, message)`.

scheduleOnce will send a specified message to the specified receiver once at the given start time.
schedule will send a specified message to the specified receiver with the period starting at the
provided startTime.
