package com.mixcasete.app.di;

import android.content.Context;
import com.mixcasete.app.util.SettingsStore;
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
public final class AppModule_ProvideSettingsFactory implements Factory<SettingsStore> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideSettingsFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SettingsStore get() {
    return provideSettings(contextProvider.get());
  }

  public static AppModule_ProvideSettingsFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideSettingsFactory(contextProvider);
  }

  public static SettingsStore provideSettings(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSettings(context));
  }
}
