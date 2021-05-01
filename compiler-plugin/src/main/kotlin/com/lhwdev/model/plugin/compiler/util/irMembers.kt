package com.lhwdev.model.plugin.compiler.util

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.TypeAliasDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.findDeclaration
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name


// IrPackageFragment

fun IrDeclarationContainer.referenceClass(name: String): IrClass =
	findDeclaration { it.name.asString() == name }
		?: error("could not reference class $name on ${debugName()}")

fun IrDeclarationContainer.referenceClass(name: Name): IrClass =
	findDeclaration { it.name == name }
		?: error("could not reference class $name on ${debugName()}")


inline fun IrDeclarationContainer.referenceFunction(
	name: String,
	predicate: (IrSimpleFunction) -> Boolean
): IrSimpleFunction = findDeclaration { it.name.asString() == name && predicate(it) }
	?: error("could not reference function $name that matches predicate on ${debugName()}")

fun IrDeclarationContainer.referenceFunction(name: String): IrSimpleFunction =
	referenceFunction(name) { true }

fun IrDeclarationContainer.referenceFunction(name: String, valueParametersCount: Int): IrSimpleFunction =
	referenceFunction(name) { it.valueParameters.size == valueParametersCount }

inline fun IrDeclarationContainer.referenceFunction(
	name: Name,
	predicate: (IrSimpleFunction) -> Boolean
): IrSimpleFunction = findDeclaration { it.name == name && predicate(it) }
	?: error("could not reference function $name that matches predicate on ${debugName()}")

fun IrDeclarationContainer.referenceFunction(name: Name): IrSimpleFunction =
	referenceFunction(name) { true }

fun IrDeclarationContainer.referenceFunction(name: Name, valueParametersCount: Int): IrSimpleFunction =
	referenceFunction(name) { it.valueParameters.size == valueParametersCount }


inline fun IrClass.referenceConstructor(
	predicate: (IrConstructor) -> Boolean
): IrConstructor = findDeclaration(predicate)!!

fun IrClass.referencePrimaryConstructor(): IrConstructor = primaryConstructor
	?: error("could not reference primary constructor: no primary constructor on ${debugName()}")

fun IrClass.referenceConstructor(valueParametersCount: Int): IrConstructor =
	referenceConstructor { it.valueParameters.size == valueParametersCount }


inline fun IrDeclarationContainer.referenceProperty(name: String, predicate: (IrProperty) -> Boolean): IrProperty =
	findDeclaration { it.name.asString() == name && predicate(it) }
		?: error("could not reference property $name that matches predicate on ${debugName()}")

fun IrDeclarationContainer.referenceProperty(name: String) = referenceProperty(name) { true }

inline fun IrDeclarationContainer.referenceProperty(name: Name, predicate: (IrProperty) -> Boolean): IrProperty =
	findDeclaration { it.name == name && predicate(it) }
		?: error("could not reference property $name that matches predicate on ${debugName()}")

fun IrDeclarationContainer.referenceProperty(name: Name) = referenceProperty(name) { true }


@OptIn(ObsoleteDescriptorBasedAPI::class)
fun IrPluginContext.referencePackage(fqName: FqName): IrPackage =
	IrPackageDescriptorImpl(this, fqName, moduleDescriptor.getPackage(fqName))


interface IrPackage {
	val fqName: FqName
	
	fun referenceClassOrNull(name: Name): IrClassSymbol?
	fun referenceClass(name: Name): IrClassSymbol
	
	fun referenceTypeAliasOrNull(name: Name): IrTypeAliasSymbol?
	fun referenceTypeAlias(name: Name): IrTypeAliasSymbol
	
	fun referenceFunctions(name: Name): Sequence<IrSimpleFunctionSymbol>
	fun referenceFirstFunction(name: Name, valueParametersCount: Int): IrSimpleFunctionSymbol =
		referenceFunctions(name).first { it.owner.valueParameters.size == valueParametersCount }
	
	fun referenceConstructors(name: Name): Sequence<IrConstructorSymbol>
	
	fun referenceProperties(name: Name): Sequence<IrPropertySymbol>
	
	fun child(name: Name): IrPackage
}


// extensions with `name: String`

fun IrPackage.referenceClassOrNull(name: String): IrClassSymbol? =
	referenceClassOrNull(Name.guessByFirstCharacter(name))

fun IrPackage.referenceClass(name: String): IrClassSymbol = referenceClass(Name.guessByFirstCharacter(name))

fun IrPackage.referenceTypeAliasOrNull(name: String): IrTypeAliasSymbol? =
	referenceTypeAliasOrNull(Name.guessByFirstCharacter(name))

fun IrPackage.referenceTypeAlias(name: String): IrTypeAliasSymbol = referenceTypeAlias(Name.guessByFirstCharacter(name))

fun IrPackage.referenceFunctions(name: String): Sequence<IrSimpleFunctionSymbol> =
	referenceFunctions(Name.guessByFirstCharacter(name))

fun IrPackage.referenceFirstFunction(name: String, valueParametersCount: Int): IrSimpleFunctionSymbol =
	referenceFirstFunction(Name.guessByFirstCharacter(name), valueParametersCount)

fun IrPackage.referenceConstructors(name: String): Sequence<IrConstructorSymbol> =
	referenceConstructors(Name.guessByFirstCharacter(name))

fun IrPackage.referenceProperties(name: String): Sequence<IrPropertySymbol> =
	referenceProperties(Name.guessByFirstCharacter(name))

fun IrPackage.referenceFirstProperty(name: String): IrPropertySymbol = referenceProperties(name).first()

fun IrPackage.child(name: String): IrPackage =
	child(Name.guessByFirstCharacter(name))


fun IrPackage.referenceFirstFunction(name: String) = referenceFunctions(name).first()


fun IrPackage.debugName(): String = "package $fqName"


// TODO: do not depend on descriptor, or at least make using it affected by altering IR tree
@OptIn(ObsoleteDescriptorBasedAPI::class)
class IrPackageDescriptorImpl(
	private val pluginContext: IrPluginContext,
	override val fqName: FqName,
	val descriptor: PackageViewDescriptor
) : IrPackage {
	private val linker = pluginContext.linker
	private val <T : IrSymbol> T.bound: T
		get() {
			if(!isBound) linker.getDeclaration(this)
			return this
		}
	
	override fun referenceClassOrNull(name: Name): IrClassSymbol? {
		val classifier = descriptor.memberScope.getContributedClassifier(name, NoLookupLocation.FROM_BACKEND)
		
		if(classifier is ClassDescriptor)
			return pluginContext.symbolTable.referenceClass(classifier).bound
		
		return null
	}
	
	override fun referenceClass(name: Name): IrClassSymbol = referenceClassOrNull(name)
		?: error("could not reference class $name on ${debugName()}")
	
	override fun referenceTypeAliasOrNull(name: Name): IrTypeAliasSymbol? {
		val classifier = descriptor.memberScope.getContributedClassifier(name, NoLookupLocation.FROM_BACKEND)
		
		if(classifier is TypeAliasDescriptor)
			return pluginContext.symbolTable.referenceTypeAlias(classifier).bound
		
		return null
	}
	
	override fun referenceTypeAlias(name: Name): IrTypeAliasSymbol = referenceTypeAliasOrNull(name)
		?: error("could not reference type alias $name on ${debugName()}")
	
	override fun referenceFunctions(name: Name): Sequence<IrSimpleFunctionSymbol> =
		descriptor.memberScope.getContributedFunctions(name, NoLookupLocation.FROM_BACKEND).asSequence().map {
			pluginContext.symbolTable.referenceSimpleFunction(it).bound
		}
	
	override fun referenceFirstFunction(name: Name, valueParametersCount: Int): IrSimpleFunctionSymbol =
		descriptor.memberScope.getContributedFunctions(name, NoLookupLocation.FROM_BACKEND)
			.first { it.valueParameters.size == valueParametersCount }
			.let {
				pluginContext.symbolTable.referenceSimpleFunction(it).bound
			}
	
	override fun referenceConstructors(name: Name): Sequence<IrConstructorSymbol> =
		referenceClass(name).owner.declarations.filterIsInstance<IrConstructor>().map { it.symbol }.asSequence()
	
	override fun referenceProperties(name: Name): Sequence<IrPropertySymbol> =
		descriptor.memberScope.getContributedVariables(name, NoLookupLocation.FROM_BACKEND).asSequence().map {
			pluginContext.symbolTable.referenceProperty(it).bound
		}
	
	override fun child(name: Name): IrPackageDescriptorImpl {
		val newPackage = fqName.child(name)
		return IrPackageDescriptorImpl(pluginContext, newPackage, descriptor.module.getPackage(newPackage))
	}
}


fun IrElementScope.referenceClassOrNull(fqName: FqName): IrClassSymbol? =
	context.referenceClass(fqName)

fun IrElementScope.referenceTypeAliasOrNull(fqName: FqName): IrTypeAliasSymbol? =
	context.referenceTypeAlias(fqName)

fun IrPluginContext.referenceClassOrFail(fqName: FqName): IrClassSymbol =
	referenceClass(fqName) ?: error("could not reference class $fqName")

fun IrPluginContext.referenceTypeAliasOrFail(fqName: FqName): IrTypeAliasSymbol =
	referenceTypeAlias(fqName) ?: error("could not reference type alias $fqName")

fun IrElementScope.referenceClass(fqName: FqName): IrClassSymbol =
	context.referenceClassOrFail(fqName)

fun IrElementScope.referenceTypeAlias(fqName: FqName): IrTypeAliasSymbol =
	context.referenceTypeAlias(fqName) ?: error("could not reference type alias $fqName")

fun IrElementScope.referenceConstructors(classFqn: FqName): Collection<IrConstructorSymbol> =
	context.referenceConstructors(classFqn)

fun IrElementScope.referenceFunctions(fqName: FqName): Collection<IrSimpleFunctionSymbol> =
	context.referenceFunctions(fqName)

fun IrElementScope.referenceProperties(fqName: FqName): Collection<IrPropertySymbol> =
	context.referenceProperties(fqName)

fun IrElementScope.referencePackage(fqName: FqName): IrPackage =
	context.referencePackage(fqName)

fun IrElementScope.referenceClassType(fqName: FqName): IrType =
	context.referenceClassOrFail(fqName).defaultType

fun IrElementScope.referenceClassType(fqName: FqName, vararg arguments: IrTypeArgument): IrType =
	context.referenceClassOrFail(fqName).typeWithArguments(arguments.toList())

fun IrElementScope.referenceClassType(fqName: FqName, vararg arguments: IrType): IrType =
	context.referenceClassOrFail(fqName).typeWith(arguments.toList())
