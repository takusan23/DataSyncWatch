package io.github.takusan23.datasyncwatchcommon

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** WearOS の API ラッパー */
object DataSyncWatchTool {

    private const val PATH = "/datasyncwatch"
    private const val TEXT_KEY = "assets_key"

    /**
     * 同期したいデータを設定する
     * テキスト以外にもバイナリデータとかも入れられるはず
     *
     * @param context [Context]
     * @param text テキスト
     */
    suspend fun sendDataSync(context: Context, text: String) {
        // Bundle みたいな感じ
        val request = PutDataMapRequest.create(PATH).apply {
            // キーと値
            dataMap.putString(TEXT_KEY, text)
        }.asPutDataRequest()
        Wearable.getDataClient(context).putDataItem(request).await()
    }

    /**
     * 同期したデータを Flow で受け取る
     *
     * @param context [Context]
     */
    fun receiveDataSync(context: Context) = callbackFlow {

        fun sendResult(dataItem: DataItem) {
            when (dataItem.uri.path) {
                PATH -> {
                    // PutDataMapRequest を復元する
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    // キーを使って値を取り出す
                    trySend(dataMap.getString(TEXT_KEY))
                }
            }
        }

        val listener = DataClient.OnDataChangedListener { dataEvents ->
            dataEvents
                .filter { it.type == DataEvent.TYPE_CHANGED }
                .forEach { event -> sendResult(event.dataItem) }
        }

        // 同期的に一回目は取得
        Wearable.getDataClient(context).dataItems.await()
            .let { buffer -> (0 until buffer.count).map { buffer.get(it) } }
            .forEach { dataItem -> sendResult(dataItem) }

        // アプリ起動中に変化した場合
        Wearable.getDataClient(context).addListener(listener)
        awaitClose { Wearable.getDataClient(context).removeListener(listener) }
    }

}