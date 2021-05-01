@file:Suppress("PropertyName")

package com.lhwdev.model.plugin.compiler

import com.lhwdev.model.plugin.compiler.util.*
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.types.impl.IrStarProjectionImpl
import org.jetbrains.kotlin.ir.types.typeWithArguments
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.*


object ModelIrNames {
	val library = FqName("com.lhwdev.model")
	
	val Model = library.child("Model")
	val Exclude = library.child("Exclude")
	
	val ModelValue = library.child("ModelValue")
	val ModelInfo = library.child("ModelInfo")
	val ModelInfoParameter = library.child("ModelInfoParameter")
	val ModelDescriptor = library.child("ModelDescriptor")
	val StructuredModelDescriptor = library.child("StructuredModelDescriptor")
	val StructuredModelDescriptorBuilder = library.child("StructuredModelDescriptorBuilder")
	
	val modelInfo = Name.identifier("modelInfo")
	
	object ModelValueClass {
		val modelInfo = Name.identifier("modelInfo")
		val readModel = Name.identifier("readModel")
		val writeModel = Name.identifier("writeModel")
	}
	
	val PrimitiveModelInfo = library.child("PrimitiveModelInfo")
	
	object PrimitiveModelInfoClass {
		val Byte = Name.identifier("Byte")
		val Short = Name.identifier("Short")
		val Int = Name.identifier("Int")
		val Long = Name.identifier("Long")
		val Float = Name.identifier("Float")
		val Double = Name.identifier("Double")
		val Boolean = Name.identifier("Boolean")
		val Char = Name.identifier("Char")
		val String = Name.identifier("String")
	}
	
	val ArrayModelInfo = library.child("ArrayModelInfo")
}

class ModelIrSymbolsClass(context: IrPluginContext) {
	val library = context.referencePackage(ModelIrNames.library)
	val ModelValue = context.referenceClassOrFail(ModelIrNames.ModelValue)
	val ModelValueClass = ModelValueClassType()
	inner class ModelValueClassType {
		val modelInfo = ModelValue.referenceProperty(ModelIrNames.ModelValueClass.modelInfo)
		val readModel = ModelValue.referenceFunction(ModelIrNames.ModelValueClass.readModel)
		val writeModel = ModelValue.referenceFunction(ModelIrNames.ModelValueClass.writeModel)
	}
	
	val ModelInfo = context.referenceClassOrFail(ModelIrNames.ModelInfo)
	val StructuredModelInfo = context.referenceClassOrFail(ModelIrNames.library.child("StructuredModelInfo"))
	val StructuredModelDescriptor = context.referenceClassOrFail(ModelIrNames.StructuredModelDescriptor)
	val StructuredModelDescriptorClass = StructuredModelDescriptorClassType()
	inner class StructuredModelDescriptorClassType {
		val getElementName = StructuredModelDescriptor.referenceFunction("getElementName")
		val getElementAt = StructuredModelDescriptor.referenceFunction("getElementAt")
	}
	
	val modelInfoAt = library.referenceFunctions("modelInfoAt").first {
		it.owner.extensionReceiverParameter?.type == StructuredModelInfo.typeWithArguments(listOf(IrStarProjectionImpl))
	}
	
	val StructuredModelDescriptorBuilder = context.referenceClassOrFail(ModelIrNames.StructuredModelDescriptorBuilder)
	val fStructuredModelDescriptor = context.referenceFunctions(ModelIrNames.StructuredModelDescriptor).first()
	
	val ModelInfoClass = ModelInfoClassType()
	inner class ModelInfoClassType {
		val modelDescriptor = ModelInfo.referenceProperty("modelDescriptor")
		val create = ModelInfo.referenceFunction("create")
	}
	
	val StructuredModelDescriptorBuilderClass = StructuredModelDescriptorBuilderClassType()
	inner class StructuredModelDescriptorBuilderClassType {
		val property = StructuredModelDescriptorBuilder.referenceFunction("property", valueParametersCount = 2)
	}
	
	val PrimitiveModelInfo = context.referenceClass(ModelIrNames.PrimitiveModelInfo)!!
	val PrimitiveModelInfoClass = PrimitiveModelInfoClassType()
	inner class PrimitiveModelInfoClassType {
		val Byte = PrimitiveModelInfo.referenceClass("Byte")
		val Short = PrimitiveModelInfo.referenceClass("Short")
		val Int = PrimitiveModelInfo.referenceClass("Int")
		val Long = PrimitiveModelInfo.referenceClass("Long")
		val Float = PrimitiveModelInfo.referenceClass("Float")
		val Double = PrimitiveModelInfo.referenceClass("Double")
		val Boolean = PrimitiveModelInfo.referenceClass("Boolean")
		val Char = PrimitiveModelInfo.referenceClass("Char")
		val String = PrimitiveModelInfo.referenceClass("String")
	}
	
	val ArrayModelInfo = context.referenceClassOrFail(ModelIrNames.ArrayModelInfo)
	
	val ModelWriter = library.referenceClass("ModelWriter")
	val ModelWriterClass = ModelWriterClassType()
	inner class ModelWriterClassType {
		val writeByte = ModelWriter.referenceFunction("writeByte")
		val writeShort = ModelWriter.referenceFunction("writeShort")
		val writeInt = ModelWriter.referenceFunction("writeInt")
		val writeLong = ModelWriter.referenceFunction("writeLong")
		val writeFloat = ModelWriter.referenceFunction("writeFloat")
		val writeDouble = ModelWriter.referenceFunction("writeDouble")
		val writeBoolean = ModelWriter.referenceFunction("writeBoolean")
		val writeChar = ModelWriter.referenceFunction("writeChar")
		val writeModel = ModelWriter.referenceFunction("writeModel")
	}
	
	val ModelReader = library.referenceClass("ModelReader")
	val ModelReaderClass = ModelReaderClassType()
	inner class ModelReaderClassType {
		val readByte = ModelReader.referenceFunction("readByte")
		val readShort = ModelReader.referenceFunction("readShort")
		val readInt = ModelReader.referenceFunction("readInt")
		val readLong = ModelReader.referenceFunction("readLong")
		val readFloat = ModelReader.referenceFunction("readFloat")
		val readDouble = ModelReader.referenceFunction("readDouble")
		val readBoolean = ModelReader.referenceFunction("readBoolean")
		val readChar = ModelReader.referenceFunction("readChar")
		val readModel = ModelReader.referenceFunction("readModel")
	}
}

private val sModelIrSymbols = WeakHashMap<IrPluginContext, ModelIrSymbolsClass>()

val ModelIrSymbols: ModelIrSymbolsClass
	get() = sModelIrSymbols.getOrPut(pluginContext) { ModelIrSymbolsClass(pluginContext) }


