package com.frank.weartimer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.tooling.preview.TilePreviewData
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"
private const val COUNTDOWN_ACTIVITY_CLASS_NAME = "com.frank.weartimer.CountdownActivity"

class HelloWorldTileService : TileService() {
    public override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile?> {
        val deviceParams = requestParams.deviceConfiguration
        val layout = if (deviceParams != null) {
            helloWorldLayout(this, deviceParams)
        } else {
            // Fallback layout when device parameters are not available
            Text.Builder(this, "Cannot load tile on this device.")
                .build()
        }

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(layout)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources?> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }
}

private fun helloWorldLayout(
    context: Context,
    deviceParams: DeviceParametersBuilders.DeviceParameters
): LayoutElementBuilders.LayoutElement {
    val clickable = ModifiersBuilders.Clickable.Builder()
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(COUNTDOWN_ACTIVITY_CLASS_NAME)
                        .addKeyToExtraMapping(
                            "timer_length",
                            ActionBuilders.intExtra(60)
                        )
                        .build()
                )
                .build()
        )
        .build()

    return PrimaryLayout.Builder(deviceParams)
        .setResponsiveContentInsetEnabled(true)
        .setPrimaryLabelTextContent(
            Text.Builder(context, "Hello World")
                .build()
        )
        .setContent(
            CompactChip.Builder(
                context,
                "1 Min Timer",
                clickable,
                deviceParams
            )
                .build()
        )
        .build()
}

@Preview
@Composable
fun HelloWorldTilePreview() {
    TilePreviewData(
        onTileRequest = { requestParams ->
            val service = HelloWorldTileService()
            service.onTileRequest(requestParams).get()!!
        }
    )
}
