/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.index.strategies

import org.locationtech.geomesa.filter._
import org.locationtech.geomesa.filter.visitor.IdExtractingVisitor
import org.locationtech.geomesa.index.api.{FilterStrategy, GeoMesaFeatureIndex, WrappedFeature}
import org.locationtech.geomesa.index.geotools.GeoMesaDataStore
import org.opengis.feature.simple.SimpleFeatureType
import org.opengis.filter.{And, Filter, Id, Or}

trait IdFilterStrategy[DS <: GeoMesaDataStore[DS, F, W, Q], F <: WrappedFeature, W, Q] extends GeoMesaFeatureIndex[DS, F, W, Q] {

  override def getFilterStrategy(sft: SimpleFeatureType, filter: Filter): Seq[FilterStrategy[DS, F, W, Q]] = {
    if (filter == Filter.INCLUDE) {
      Seq(FilterStrategy(this, None, None))
    } else if (filter == Filter.EXCLUDE) {
      Seq.empty
    } else {
      val (ids, notIds) = IdExtractingVisitor(filter)
      if (ids.isDefined) {
        Seq(FilterStrategy(this, ids, notIds))
      } else {
        Seq(FilterStrategy(this, None, Some(filter)))
      }
    }
  }

  // top-priority index - always 1 if there are actually ID filters
  override def getCost(sft: SimpleFeatureType,
                       ds: Option[DS],
                       filter: FilterStrategy[DS, F, W, Q],
                       transform: Option[SimpleFeatureType]): Long = {
    if (filter.primary.isDefined) IdFilterStrategy.StaticCost else Long.MaxValue
  }
}

object IdFilterStrategy {

  val StaticCost = 1L

  def intersectIdFilters(filter: Filter): Set[String] = {
    import scala.collection.JavaConversions._
    filter match {
      case f: And => f.getChildren.map(intersectIdFilters).reduceLeftOption(_ intersect _).getOrElse(Set.empty)
      case f: Or  => f.getChildren.flatMap(intersectIdFilters).toSet
      case f: Id  => f.getIDs.map(_.toString).toSet
      case _ => throw new IllegalArgumentException(s"Expected ID filter, got ${filterToString(filter)}")
    }
  }
}
