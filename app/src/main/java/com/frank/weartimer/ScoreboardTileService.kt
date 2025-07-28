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
import androidx.wear.tiles.ColorBuilders

private const val RESOURCES_VERSION = "1"
private const val PREFS_NAME = "timer_scores"
private const val KEY_X_SCORE = "xScore"
private const val KEY_O_SCORE = "oScore"
private const val TICTACTOE_ACTIVITY_CLASS_NAME = "com.frank.weartimer.TicTacToeActivity"

class ScoreboardTileService : TileService() {
    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<TileBuilders.Tile> {
        // Always read fresh scores from SharedPreferences
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val xScore = prefs.getInt(KEY_X_SCORE, 0)
        val oScore = prefs.getInt(KEY_O_SCORE, 0)

        // Set flag to reset game when tile is tapped
        prefs.edit().putBoolean("should_reset_game", true).apply()

        val launchAction = ActionBuilders.LaunchAction.Builder().setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(packageName)
                .setClassName(TICTACTOE_ACTIVITY_CLASS_NAME)
                .build()
        ).build()

        val clickable =
            ModifiersBuilders.Clickable.Builder().setOnClick(launchAction).build()

        val column = LayoutElementBuilders.Column.Builder()
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("Scoreboard")
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.DpProp.Builder().setValue(16f).build())
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Row.Builder()
                    .addContent(
                        LayoutElementBuilders.Column.Builder()
                            .addContent(
                                LayoutElementBuilders.Text.Builder()
                                    .setText("Player X")
                                    .build()
                            )
                            .addContent(
                                LayoutElementBuilders.Text.Builder()
                                    .setText("$xScore")
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setWidth(DimensionBuilders.DpProp.Builder().setValue(32f).build())
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Column.Builder()
                            .addContent(
                                LayoutElementBuilders.Text.Builder()
                                    .setText("Player O")
                                    .build()
                            )
                            .addContent(
                                LayoutElementBuilders.Text.Builder()
                                    .setText("$oScore")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build())
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