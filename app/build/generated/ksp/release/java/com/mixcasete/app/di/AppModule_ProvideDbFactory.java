package com.mixcasete.app.di;

import android.content.Context;
import com.mixcasete.app.data.db.AppDb;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class AppModule_ProvideDbFactory implements Factory<AppDb> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideDbFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AppDb get() {
    return provideDb(contextProvider.get());
  }

  public static AppModule_ProvideDbFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideDbFactory(contextProvider);
  }

  public static AppDb provideDb(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideDb(context));
  }
}
