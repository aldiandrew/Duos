package com.aldiandrew.duos

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

interface IDuosOverlay : IInterface {
    fun isReady(): Boolean
    fun getError(): String

    abstract class Stub : Binder(), IDuosOverlay {

        companion object {
            private const val DESCRIPTOR = "com.aldiandrew.duos.IDuosOverlay"
            private const val TRANSACTION_IS_READY = IBinder.FIRST_CALL_TRANSACTION
            private const val TRANSACTION_GET_ERROR = IBinder.FIRST_CALL_TRANSACTION + 1

            fun asInterface(binder: IBinder?): IDuosOverlay? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                if (local is IDuosOverlay) return local
                return Proxy(binder)
            }
        }

        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int
        ): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }

                TRANSACTION_IS_READY -> {
                    data.enforceInterface(DESCRIPTOR)
                    reply?.writeNoException()
                    reply?.writeInt(if (isReady()) 1 else 0)
                    return true
                }

                TRANSACTION_GET_ERROR -> {
                    data.enforceInterface(DESCRIPTOR)
                    reply?.writeNoException()
                    reply?.writeString(getError())
                    return true
                }
            }

            return super.onTransact(code, data, reply, flags)
        }

        private class Proxy(
            private val remote: IBinder
        ) : IDuosOverlay {

            override fun asBinder(): IBinder = remote

            override fun isReady(): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(
                        TRANSACTION_IS_READY,
                        data,
                        reply,
                        0
                    )
                    reply.readException()
                    reply.readInt() != 0
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }

            override fun getError(): String {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(
                        TRANSACTION_GET_ERROR,
                        data,
                        reply,
                        0
                    )
                    reply.readException()
                    reply.readString().orEmpty()
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }
}
