package dev.co508.ticketspace.ar

import dev.co508.ticketspace.ar.math.Pose
import com.google.ar.core.Pose as CorePose

fun CorePose.roomPose(): Pose = Pose(translation.toList(), rotationQuaternion.toList())

fun Pose.corePose(): CorePose = CorePose(t.toFloatArray(), q.toFloatArray())
