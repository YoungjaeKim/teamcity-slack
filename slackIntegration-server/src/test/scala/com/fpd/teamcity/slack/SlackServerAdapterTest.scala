package com.fpd.teamcity.slack

import java.util.Collections

import com.fpd.teamcity.slack.ConfigManager.BuildSettingFlag
import com.fpd.teamcity.slack.ConfigManager.BuildSettingFlag.BuildSettingFlag
import com.fpd.teamcity.slack.SlackServerAdapter._
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.serverSide.{BuildHistory, SBuildServer, SFinishedBuild, SRunningBuild}
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.scalatest.prop.TableDrivenPropertyChecks._

class SlackServerAdapterTest extends FlatSpec with MockFactory with Matchers {
  "SlackServerAdapter.statusChanged" should "work properly" in {
    forAll(data) { (previous: Status, current: Status, changed: Boolean) ⇒
      statusChanged(previous, current) shouldEqual changed
    }

    def data =
      Table(
        ("previous", "current", "changed"), // First tuple defines column names
        // Subsequent tuples define the data
        (Status.FAILURE, Status.NORMAL, true),
        (Status.ERROR, Status.NORMAL, true),
        (Status.NORMAL, Status.FAILURE, true),
        (Status.NORMAL, Status.ERROR, true),
        (Status.UNKNOWN, Status.FAILURE, false),
        (Status.UNKNOWN, Status.NORMAL, false),
        (Status.NORMAL, Status.UNKNOWN, false),
        (Status.FAILURE, Status.UNKNOWN, false),
        (Status.FAILURE, Status.FAILURE, false),
        (Status.NORMAL, Status.NORMAL, false),
        (Status.ERROR, Status.ERROR, false),
        (Status.UNKNOWN, Status.UNKNOWN, false)
      )
  }

  "SlackServerAdapter.calculateFlags" should "work properly" in {
    forAll(data) { (previous: Status, current: Status, flags: Set[BuildSettingFlag]) ⇒
      calculateFlags(previous, current) shouldEqual flags
    }

    import BuildSettingFlag._
    def data =
      Table(
        ("previous", "current", "flags"), // First tuple defines column names
        // Subsequent tuples define the data
        (Status.FAILURE, Status.NORMAL, Set(success, failureToSuccess)),
        (Status.ERROR, Status.NORMAL, Set(success, failureToSuccess)),
        (Status.NORMAL, Status.FAILURE, Set(failure, successToFailure)),
        (Status.NORMAL, Status.ERROR, Set(failure, successToFailure)),
        (Status.UNKNOWN, Status.FAILURE, Set(failure)),
        (Status.UNKNOWN, Status.ERROR, Set(failure)),
        (Status.UNKNOWN, Status.NORMAL, Set(success))
      )
  }

  "SlackServerAdapter.buildFinished" should "call notify with success flag" in new CommonMocks {
    val buildServer: SBuildServer = stub[SBuildServer]
    val adapter = new SlackServerAdapter(buildServer, manager, stub[SlackGateway], stub[MessageBuilderFactory])

    val buildHistory: BuildHistory = stub[BuildHistory]
    val build: SRunningBuild = stub[SRunningBuild]

    buildHistory.getEntriesBefore _ when(build, false) returns Collections.emptyList[SFinishedBuild]
    buildServer.getHistory _ when() returns buildHistory
    build.getBuildStatus _ when() returns Status.NORMAL

    adapter.buildFinished(build)

    adapter.send _ verify (build, Set(BuildSettingFlag.success))
  }
}
