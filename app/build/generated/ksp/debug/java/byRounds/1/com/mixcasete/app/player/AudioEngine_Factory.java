package com.mixcasete.app.player;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class AudioEngine_Factory implements Factory<AudioEngine> {
  private final Provider<Context> contextProvider;

  public AudioEngine_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AudioEngine get() {
    return newInstance(contextProvider.get());
  }

  public static AudioEngine_Factory create(Provider<Context> contextProvider) {
    return new AudioEngine_Factory(contextProvider);
  }

  public static AudioEngine newInstance(Context context) {
    return new AudioEngine(context);
  }
}
