package com.mixcasete.app.data;

import android.content.Context;
import com.mixcasete.app.data.db.TrackDao;
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
public final class Repository_Factory implements Factory<Repository> {
  private final Provider<Context> contextProvider;

  private final Provider<TrackDao> daoProvider;

  public Repository_Factory(Provider<Context> contextProvider, Provider<TrackDao> daoProvider) {
    this.contextProvider = contextProvider;
    this.daoProvider = daoProvider;
  }

  @Override
  public Repository get() {
    return newInstance(contextProvider.get(), daoProvider.get());
  }

  public static Repository_Factory create(Provider<Context> contextProvider,
      Provider<TrackDao> daoProvider) {
    return new Repository_Factory(contextProvider, daoProvider);
  }

  public static Repository newInstance(Context context, TrackDao dao) {
    return new Repository(context, dao);
  }
}
