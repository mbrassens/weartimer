package com.frank.weartimer

import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.ColorBuilders // This is not strictly needed for this specific tile logic


private const val RESOURCES_VERSION = "1"
private const val COUNTDOWN_ACTIVITY_CLASS_NAME = "com.frank.weartimer.CountdownActivity"
private const val EXTRA_TIMER_LENGTH_KEY = "timer_length" // Define the extra key

class TimerTileService : TileService() {
    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<TileBuilders.Tile> {
        // Check if a timer is already running
        val isTimerRunning = CountdownActivity.isTimerRunning(applicationContext)
        
        // Create actions for each timer duration (only if no timer is running)
        val oneMinAction = ActionBuilders.LaunchAction.Builder().setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(packageName)
                .setClassName(COUNTDOWN_ACTIVITY_CLASS_NAME)
                .addKeyToExtraMapping("timer_length", ActionBuilders.intExtra(if (isTimerRunning) -1 else 60))
                .build()
        ).build()

        val twoMinAction = ActionBuilders.LaunchAction.Builder().setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(packageName)
                .setClassName(COUNTDOWN_ACTIVITY_CLASS_NAME)
                .addKeyToExtraMapping("timer_length", ActionBuilders.intExtra(if (isTimerRunning) -1 else 120))
                .build()
        ).build()

        val fiveMinAction = ActionBuilders.LaunchAction.Builder().setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(packageName)
                .setClassName(COUNTDOWN_ACTIVITY_CLASS_NAME)
                .addKeyToExtraMapping("timer_length", ActionBuilders.intExtra(if (isTimerRunning) -1 else 300))
                .build()
        ).build()

        // Create clickable modifiers for each button
        val oneMinClickable = ModifiersBuilders.Clickable.Builder().setOnClick(oneMinAction).build()
        val twoMinClickable = ModifiersBuilders.Clickable.Builder().setOnClick(twoMinAction).build()
        val fiveMinClickable = ModifiersBuilders.Clickable.Builder().setOnClick(fiveMinAction).build()

        // Create the tile layout with three timer buttons
        val column = LayoutElementBuilders.Column.Builder()
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(if (isTimerRunning) "Timer Running" else "Quick Timer")
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.DpProp.Builder().setValue(8f).build())
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Row.Builder()
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(if (isTimerRunning) "View" else "1min")
                            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(oneMinClickable).build())
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setWidth(DimensionBuilders.DpProp.Builder().setValue(4f).build())
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(if (isTimerRunning) "Timer" else "2min")
                            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(twoMinClickable).build())
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setWidth(DimensionBuilders.DpProp.Builder().setValue(4f).build())
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(if (isTimerRunning) "Now" else "5min")
                            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(fiveMinClickable).build())
                            .build()
                    )
                    .build()
            )
            .build()

        val layout = LayoutElementBuilders.Layout.Builder().setRoot(column).build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTimeline(
                TimelineBuilders.Timeline.Builder().addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(layout)
                        .build()
                ).build()
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return Futures.immediateFuture(resources)
    }
}