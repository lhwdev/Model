package com.lhwdev.model.plugin.compiler

import com.lhwdev.model.plugin.compiler.util.IrElementScope
import com.lhwdev.model.plugin.compiler.util.irCall
import com.lhwdev.model.plugin.compiler.util.irGetObject
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IdSignatureValues
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isPrimitiveType


fun IrElementScope.irModelInfoFor(type: IrType): IrExpression? = when {
	type !is IrSimpleType -> error("not IrSimpleType")
	type.isPrimitiveType() -> when(type.classifier.signature) {
		IdSignatureValues._byte -> ModelIrSymbols.PrimitiveModelInfoClass.Byte
		IdSignatureValues._short -> ModelIrSymbols.PrimitiveModelInfoClass.Short
		IdSignatureValues._int -> ModelIrSymbols.PrimitiveModelInfoClass.Int
		IdSignatureValues._long -> ModelIrSymbols.PrimitiveModelInfoClass.Long
		IdSignatureValues._boolean -> ModelIrSymbols.PrimitiveModelInfoClass.Boolean
		IdSignatureValues._float -> ModelIrSymbols.PrimitiveModelInfoClass.Float
		IdSignatureValues._long -> ModelIrSymbols.PrimitiveModelInfoClass.Long
		IdSignatureValues._char -> ModelIrSymbols.PrimitiveModelInfoClass.Char
		else -> error("not primitive type but primitive type??")
	}.let { irGetObject(it) }
	
	// type.isArray() -> irConstructorCall( // not primitive array like IntArray, only Array<T>
	// 	ModelIrSymbols.ArrayModelInfo.referencePrimaryConstructor(),
	// 	valueArguments = listOf(
	// 		// elementClass
	// 		irTODO(),
	//
	// 		// elementInfo
	// 		when(val elementType = type.arguments.single()) {
	// 			is IrStarProjection -> TODO("unsupported star projection model")
	// 			is IrTypeProjection -> when(elementType.variance) {
	// 				Variance.INVARIANT -> elementType.type
	// 				Variance.IN_VARIANCE -> TODO("unsupported variance")
	// 				Variance.OUT_VARIANCE -> TODO("unsupported variance")
	// 			}
	// 			else -> error("unknown type argument class: $elementType for element type for ${property.debugName()}")
	// 		}
	// 	)
	// )
	
	else -> null
}

fun IrElementScope.irWriteModelTo(
	writer: IrExpression,
	value: IrExpression,
	type: IrType, modelInfo: IrExpression?
): IrExpression = when {
	type !is IrSimpleType -> error("not IrSimpleType")
	
	type.isPrimitiveType() -> when(type.classifier.signature) {
		IdSignatureValues._byte -> ModelIrSymbols.ModelWriterClass.writeByte
		IdSignatureValues._short -> ModelIrSymbols.ModelWriterClass.writeShort
		IdSignatureValues._int -> ModelIrSymbols.ModelWriterClass.writeInt
		IdSignatureValues._long -> ModelIrSymbols.ModelWriterClass.writeLong
		IdSignatureValues._boolean -> ModelIrSymbols.ModelWriterClass.writeBoolean
		IdSignatureValues._float -> ModelIrSymbols.ModelWriterClass.writeFloat
		IdSignatureValues._long -> ModelIrSymbols.ModelWriterClass.writeLong
		IdSignatureValues._char -> ModelIrSymbols.ModelWriterClass.writeChar
		else -> error("not primitive type but primitive type??")
	}.let { irCall(it, dispatchReceiver = writer, valueArguments = listOf(value)) }
	
	else -> irCall(
		ModelIrSymbols.ModelWriterClass.writeModel,
		dispatchReceiver = writer, valueArguments = listOf(value, modelInfo!!),
		typeArguments = listOf(type)
	)
}

fun IrElementScope.irReadModelFrom(
	reader: IrExpression,
	type: IrType, modelInfo: IrExpression?
): IrExpression = when {
	type !is IrSimpleType -> error("not IrSimpleType")
	
	type.isPrimitiveType() -> when(type.classifier.signature) {
		IdSignatureValues._byte -> ModelIrSymbols.ModelReaderClass.readByte
		IdSignatureValues._short -> ModelIrSymbols.ModelReaderClass.readShort
		IdSignatureValues._int -> ModelIrSymbols.ModelReaderClass.readInt
		IdSignatureValues._long -> ModelIrSymbols.ModelReaderClass.readLong
		IdSignatureValues._boolean -> ModelIrSymbols.ModelReaderClass.readBoolean
		IdSignatureValues._float -> ModelIrSymbols.ModelReaderClass.readFloat
		IdSignatureValues._long -> ModelIrSymbols.ModelReaderClass.readLong
		IdSignatureValues._char -> ModelIrSymbols.ModelReaderClass.readChar
		else -> error("not primitive type but primitive type??")
	}.let { irCall(it, dispatchReceiver = reader) }
	
	else -> irCall(
		ModelIrSymbols.ModelReaderClass.readModel,
		dispatchReceiver = reader, valueArguments = listOf(modelInfo!!),
		typeArguments = listOf(type)
	)
}
